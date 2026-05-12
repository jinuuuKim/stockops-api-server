package com.stockops.service;

import com.stockops.dto.AddInboundItemRequest;
import com.stockops.dto.CreateInboundRequest;
import com.stockops.dto.CreateProductRequest;
import com.stockops.dto.ExcelEntityType;
import com.stockops.dto.ExcelImportResult;
import com.stockops.dto.ExcelImportRowError;
import com.stockops.entity.Center;
import com.stockops.entity.Location;
import com.stockops.entity.Product;
import com.stockops.entity.PurchaseOrder;
import com.stockops.entity.PurchaseOrderItem;
import com.stockops.exception.InvalidOperationException;
import com.stockops.repository.CenterRepository;
import com.stockops.repository.LocationRepository;
import com.stockops.repository.ProductRepository;
import com.stockops.repository.PurchaseOrderItemRepository;
import com.stockops.repository.PurchaseOrderRepository;
import com.stockops.repository.WarehouseRepository;
import com.stockops.security.CurrentUserProvider;
import com.stockops.security.ScopeGuard;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for Excel template generation and XLSX batch imports.
 * Parses supported sheets row by row and returns detailed validation errors without aborting the full upload.
 *
 * @author StockOps Team
 * @since 1.0
 * @see ProductService
 * @see InboundService
 * @see PurchaseOrderService
 */
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private static final int HEADER_ROW_INDEX = 0;
    private static final int DATA_START_ROW_INDEX = 1;

    private final ProductService productService;
    private final InboundService inboundService;
    private final PurchaseOrderService purchaseOrderService;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final CenterRepository centerRepository;
    private final WarehouseRepository warehouseRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ScopeGuard scopeGuard;
    private final Validator validator;

    /**
     * Generates a template workbook for the requested entity type.
     *
     * @param entityType entity type to generate
     * @return template workbook bytes
     * @throws InvalidOperationException when workbook generation fails
     */
    public byte[] generateTemplate(final ExcelEntityType entityType) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            switch (entityType) {
                case PRODUCTS -> createProductsTemplate(workbook);
                case INBOUNDS -> createInboundsTemplate(workbook);
                case PURCHASE_ORDERS -> createPurchaseOrdersTemplate(workbook);
                default -> throw new InvalidOperationException("Unsupported Excel entity type: " + entityType);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new InvalidOperationException("Failed to generate Excel template");
        }
    }

    /**
     * Imports a supported XLSX workbook and returns a detailed report.
     *
     * @param entityType entity type being imported
     * @param file uploaded XLSX workbook
     * @return import summary with row-level errors
     * @throws InvalidOperationException when the file is missing or not XLSX
     */
    public ExcelImportResult importWorkbook(final ExcelEntityType entityType, final MultipartFile file) {
        validateFile(file);

        try (InputStream inputStream = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            return switch (entityType) {
                case PRODUCTS -> importProducts(workbook.getSheetAt(0));
                case INBOUNDS -> importInbounds(workbook.getSheetAt(0));
                case PURCHASE_ORDERS -> importPurchaseOrders(workbook.getSheetAt(0));
            };
        } catch (IOException exception) {
            throw new InvalidOperationException("Failed to read uploaded XLSX file");
        } catch (RuntimeException exception) {
            throw new InvalidOperationException("Invalid XLSX workbook content");
        }
    }

    private ExcelImportResult importProducts(final Sheet sheet) {
        final List<ExcelImportRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;
        final DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);

        for (int rowIndex = DATA_START_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            final Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) {
                continue;
            }

            totalRows++;
            final int excelRowNumber = rowIndex + 1;
            final String barcode = getTrimmedString(row, 0, dataFormatter);

            try {
                final CreateProductRequest request = new CreateProductRequest(
                        requireText(barcode, "barcode"),
                        requireText(getTrimmedString(row, 1, dataFormatter), "name"),
                        nullableText(getTrimmedString(row, 2, dataFormatter)),
                        nullableText(getTrimmedString(row, 3, dataFormatter)),
                        null,
                        requireText(getTrimmedString(row, 4, dataFormatter), "unit"),
                        parseBoolean(row, 5, dataFormatter, "expiryManaged"),
                        parseBigDecimal(row, 6, dataFormatter, "defaultPrice", false),
                        parseInteger(row, 7, dataFormatter, "safetyStockQuantity", false)
                );

                validateRequest(request);
                productService.createProduct(request);
                successCount++;
            } catch (RuntimeException exception) {
                errors.add(new ExcelImportRowError(excelRowNumber, barcode, exception.getMessage()));
            }
        }

        return buildResult(ExcelEntityType.PRODUCTS, totalRows, successCount, errors);
    }

    private ExcelImportResult importInbounds(final Sheet sheet) {
        final List<ExcelImportRowError> errors = new ArrayList<>();
        final Map<String, InboundContext> inboundContexts = new HashMap<>();
        int totalRows = 0;
        int successCount = 0;
        final DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);
        final Long currentUserId = currentUserProvider.getCurrentUserId();

        for (int rowIndex = DATA_START_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            final Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) {
                continue;
            }

            totalRows++;
            final int excelRowNumber = rowIndex + 1;
            final String inboundReference = getTrimmedString(row, 0, dataFormatter);

            try {
                final String reference = requireText(inboundReference, "inboundReference");
                final LocalDate inboundDate = parseLocalDate(row, 1, dataFormatter, "inboundDate", false);
                final String supplier = nullableText(getTrimmedString(row, 2, dataFormatter));
                final String productBarcode = requireText(getTrimmedString(row, 3, dataFormatter), "productBarcode");
                final String lotNumber = requireText(getTrimmedString(row, 4, dataFormatter), "lotNumber");
                final LocalDate expiryDate = parseLocalDate(row, 5, dataFormatter, "expiryDate", false);
                final Integer quantity = parseInteger(row, 6, dataFormatter, "quantity", true);
                final String locationCode = requireText(getTrimmedString(row, 7, dataFormatter), "locationCode");

                final Product product = productRepository.findByBarcodeAndDeletedFalse(productBarcode)
                        .orElseThrow(() -> new InvalidOperationException("Product not found: " + productBarcode));
                final Location location = locationRepository.findByCode(locationCode)
                        .orElseThrow(() -> new InvalidOperationException("Location not found: " + locationCode));
                scopeGuard.assertLocationAccess(location.getId());

                final AddInboundItemRequest itemRequest = new AddInboundItemRequest(
                        product.getId(),
                        lotNumber,
                        expiryDate,
                        quantity,
                        location.getId());
                validateRequest(itemRequest);

                final InboundContext context = inboundContexts.get(reference);
                if (context != null) {
                    validateInboundHeaderConsistency(context, inboundDate, supplier, reference);
                    inboundService.addItem(context.inboundId(), itemRequest);
                } else {
                    final Long inboundId = inboundService.createInbound(
                            new CreateInboundRequest(inboundDate, supplier),
                            currentUserId).id();
                    inboundContexts.put(reference, new InboundContext(inboundId, inboundDate, supplier));
                    inboundService.addItem(inboundId, itemRequest);
                }

                successCount++;
            } catch (RuntimeException exception) {
                errors.add(new ExcelImportRowError(excelRowNumber, inboundReference, exception.getMessage()));
            }
        }

        return buildResult(ExcelEntityType.INBOUNDS, totalRows, successCount, errors);
    }

    private ExcelImportResult importPurchaseOrders(final Sheet sheet) {
        final List<ExcelImportRowError> errors = new ArrayList<>();
        final Map<String, PurchaseOrderContext> purchaseOrderContexts = new HashMap<>();
        int totalRows = 0;
        int successCount = 0;
        final DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);

        for (int rowIndex = DATA_START_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            final Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) {
                continue;
            }

            totalRows++;
            final int excelRowNumber = rowIndex + 1;
            final String purchaseOrderReference = getTrimmedString(row, 0, dataFormatter);

            try {
                final String reference = requireText(purchaseOrderReference, "purchaseOrderReference");
                final String centerCode = requireText(getTrimmedString(row, 1, dataFormatter), "centerCode");
                final String warehouseCode = nullableText(getTrimmedString(row, 2, dataFormatter));
                final String supplierName = nullableText(getTrimmedString(row, 3, dataFormatter));
                final String supplierCode = nullableText(getTrimmedString(row, 4, dataFormatter));
                final String productBarcode = requireText(getTrimmedString(row, 5, dataFormatter), "productBarcode");
                final Integer requestedQuantity = parseInteger(row, 6, dataFormatter, "requestedQuantity", true);
                final BigDecimal unitPrice = parseBigDecimal(row, 7, dataFormatter, "unitPrice", false);
                final String note = nullableText(getTrimmedString(row, 8, dataFormatter));

                final Center center = centerRepository.findByCode(centerCode)
                        .orElseThrow(() -> new InvalidOperationException("Center not found: " + centerCode));
                final Long warehouseId = resolveWarehouseId(center.getId(), warehouseCode);
                scopeGuard.assertCenterWarehouseAccess(center.getId(), warehouseId);
                final Product product = productRepository.findByBarcodeAndDeletedFalse(productBarcode)
                        .orElseThrow(() -> new InvalidOperationException("Product not found: " + productBarcode));

                final PurchaseOrderContext existingContext = purchaseOrderContexts.get(reference);
                if (existingContext != null) {
                    validatePurchaseOrderConsistency(existingContext, centerCode, warehouseCode, supplierName, supplierCode, reference);
                }

                final PurchaseOrderContext context = purchaseOrderContexts.computeIfAbsent(reference, ignored -> {
                    final PurchaseOrder purchaseOrder = purchaseOrderService.create(center.getId(), warehouseId, null);
                    purchaseOrder.setSupplierName(supplierName);
                    purchaseOrder.setSupplierCode(supplierCode);
                    purchaseOrder.setNotes(note == null ? "Imported from Excel reference: " + reference : note);
                    final PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(purchaseOrder);
                    return new PurchaseOrderContext(savedPurchaseOrder, centerCode, warehouseCode, supplierName, supplierCode);
                });

                final PurchaseOrder savedPurchaseOrder = purchaseOrderService.addItem(context.purchaseOrder().getId(), product.getId(), requestedQuantity);
                if (unitPrice != null || note != null) {
                    updateLastPurchaseOrderItem(savedPurchaseOrder, unitPrice, note, requestedQuantity);
                }
                successCount++;
            } catch (RuntimeException exception) {
                errors.add(new ExcelImportRowError(excelRowNumber, purchaseOrderReference, exception.getMessage()));
            }
        }

        return buildResult(ExcelEntityType.PURCHASE_ORDERS, totalRows, successCount, errors);
    }

    private ExcelImportResult buildResult(final ExcelEntityType entityType,
                                          final int totalRows,
                                          final int successCount,
                                          final List<ExcelImportRowError> errors) {
        return new ExcelImportResult(entityType, totalRows, successCount, errors.size(), List.copyOf(errors));
    }

    private void updateLastPurchaseOrderItem(final PurchaseOrder purchaseOrder,
                                             final BigDecimal unitPrice,
                                             final String note,
                                             final Integer requestedQuantity) {
        if (purchaseOrder.getItems().isEmpty()) {
            return;
        }

        final PurchaseOrderItem lastItem = purchaseOrder.getItems().get(purchaseOrder.getItems().size() - 1);
        if (unitPrice != null) {
            lastItem.setUnitPrice(unitPrice);
            lastItem.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(requestedQuantity.longValue())));
        }
        if (note != null) {
            lastItem.setNote(note);
        }
        purchaseOrderItemRepository.save(lastItem);
    }

    private Long resolveWarehouseId(final Long centerId, final String warehouseCode) {
        if (warehouseCode == null) {
            return null;
        }

        return warehouseRepository.findByCenterIdAndCode(centerId, warehouseCode)
                .map(warehouse -> warehouse.getId())
                .orElseThrow(() -> new InvalidOperationException("Warehouse not found in center: " + warehouseCode));
    }

    private void validateInboundHeaderConsistency(final InboundContext context,
                                                  final LocalDate inboundDate,
                                                  final String supplier,
                                                  final String reference) {
        if (!sameLocalDate(context.inboundDate(), inboundDate)) {
            throw new InvalidOperationException("Inbound reference '" + reference + "' uses inconsistent inboundDate values");
        }

        if (!sameText(context.supplier(), supplier)) {
            throw new InvalidOperationException("Inbound reference '" + reference + "' uses inconsistent supplier values");
        }
    }

    private void validatePurchaseOrderConsistency(final PurchaseOrderContext context,
                                                  final String centerCode,
                                                  final String warehouseCode,
                                                  final String supplierName,
                                                  final String supplierCode,
                                                  final String reference) {
        if (!sameText(context.centerCode(), centerCode)) {
            throw new InvalidOperationException("Purchase order reference '" + reference + "' uses inconsistent centerCode values");
        }
        if (!sameText(context.warehouseCode(), warehouseCode)) {
            throw new InvalidOperationException("Purchase order reference '" + reference + "' uses inconsistent warehouseCode values");
        }
        if (!sameText(context.supplierName(), supplierName)) {
            throw new InvalidOperationException("Purchase order reference '" + reference + "' uses inconsistent supplierName values");
        }
        if (!sameText(context.supplierCode(), supplierCode)) {
            throw new InvalidOperationException("Purchase order reference '" + reference + "' uses inconsistent supplierCode values");
        }
    }

    private boolean sameLocalDate(final LocalDate left, final LocalDate right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean sameText(final String left, final String right) {
        return left == null ? right == null : left.equals(right);
    }

    private void validateRequest(final Object request) {
        final List<String> messages = validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .toList();

        if (!messages.isEmpty()) {
            throw new InvalidOperationException(String.join(", ", messages));
        }
    }

    private String requireText(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidOperationException("Column '" + fieldName + "' is required");
        }
        return value;
    }

    private String nullableText(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String getTrimmedString(final Row row, final int columnIndex, final DataFormatter formatter) {
        if (row == null) {
            return null;
        }
        final Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }
        final String value = formatter.formatCellValue(cell);
        return value == null ? null : value.trim();
    }

    private boolean parseBoolean(final Row row,
                                 final int columnIndex,
                                 final DataFormatter formatter,
                                 final String fieldName) {
        final String rawValue = requireText(getTrimmedString(row, columnIndex, formatter), fieldName);
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "true", "y", "yes", "1" -> true;
            case "false", "n", "no", "0" -> false;
            default -> throw new InvalidOperationException("Column '" + fieldName + "' must be TRUE/FALSE");
        };
    }

    private Integer parseInteger(final Row row,
                                 final int columnIndex,
                                 final DataFormatter formatter,
                                 final String fieldName,
                                 final boolean required) {
        final String rawValue = getTrimmedString(row, columnIndex, formatter);
        if (!required && (rawValue == null || rawValue.isBlank())) {
            return null;
        }

        try {
            final Integer parsedValue = Integer.valueOf(requireText(rawValue, fieldName));
            if (parsedValue <= 0 && required) {
                throw new InvalidOperationException("Column '" + fieldName + "' must be greater than zero");
            }
            if (parsedValue < 0 && !required) {
                throw new InvalidOperationException("Column '" + fieldName + "' must not be negative");
            }
            return parsedValue;
        } catch (NumberFormatException exception) {
            throw new InvalidOperationException("Column '" + fieldName + "' must be an integer");
        }
    }

    private BigDecimal parseBigDecimal(final Row row,
                                       final int columnIndex,
                                       final DataFormatter formatter,
                                       final String fieldName,
                                       final boolean required) {
        final String rawValue = getTrimmedString(row, columnIndex, formatter);
        if (!required && (rawValue == null || rawValue.isBlank())) {
            return null;
        }

        try {
            final BigDecimal parsedValue = new BigDecimal(requireText(rawValue, fieldName));
            if (parsedValue.signum() < 0) {
                throw new InvalidOperationException("Column '" + fieldName + "' must not be negative");
            }
            return parsedValue;
        } catch (NumberFormatException exception) {
            throw new InvalidOperationException("Column '" + fieldName + "' must be a decimal number");
        }
    }

    private LocalDate parseLocalDate(final Row row,
                                     final int columnIndex,
                                     final DataFormatter formatter,
                                     final String fieldName,
                                     final boolean required) {
        if (row == null) {
            if (required) {
                throw new InvalidOperationException("Column '" + fieldName + "' is required");
            }
            return null;
        }

        final Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            if (required) {
                throw new InvalidOperationException("Column '" + fieldName + "' is required");
            }
            return null;
        }

        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        final String rawValue = getTrimmedString(row, columnIndex, formatter);
        if (!required && (rawValue == null || rawValue.isBlank())) {
            return null;
        }

        try {
            return LocalDate.parse(requireText(rawValue, fieldName));
        } catch (DateTimeParseException exception) {
            throw new InvalidOperationException("Column '" + fieldName + "' must use yyyy-MM-dd format");
        }
    }

    private boolean isRowEmpty(final Row row) {
        if (row == null) {
            return true;
        }
        final DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (int cellIndex = row.getFirstCellNum(); cellIndex < row.getLastCellNum(); cellIndex++) {
            if (cellIndex < 0) {
                continue;
            }
            final Cell cell = row.getCell(cellIndex);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                final String text = formatter.formatCellValue(cell);
                if (text != null && !text.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void validateFile(final MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidOperationException("Please upload an XLSX file");
        }

        final String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new InvalidOperationException("Only XLSX files are supported");
        }
    }

    private void createProductsTemplate(final Workbook workbook) {
        final Sheet sheet = workbook.createSheet("products");
        writeHeaderRow(workbook, sheet, "barcode", "name", "description", "category", "unit", "expiryManaged", "defaultPrice", "safetyStockQuantity");
        writeExampleRow(sheet, 1, "8801234567890", "콜드브루 라떼", "350ml 캔", "음료", "EA", "TRUE", "2300", "20");
        writeInstructionsSheet(workbook, "Products Import", List.of(
                "Upload only .xlsx files generated from this template.",
                "Each row creates one product.",
                "barcode, name, unit, expiryManaged are required.",
                "defaultPrice and safetyStockQuantity must be zero or positive."
        ));
        autoSizeColumns(sheet, 8);
    }

    private void createInboundsTemplate(final Workbook workbook) {
        final Sheet sheet = workbook.createSheet("inbounds");
        writeHeaderRow(workbook, sheet, "inboundReference", "inboundDate", "supplier", "productBarcode", "lotNumber", "expiryDate", "quantity", "locationCode");
        writeExampleRow(sheet, 1, "INB-APR-001", "2026-04-13", "서울식품", "8801234567890", "LOT-20260413-01", "2026-09-30", "48", "RECEIVING-01");
        writeInstructionsSheet(workbook, "Inbounds Import", List.of(
                "Rows with the same inboundReference are grouped into one draft inbound document.",
                "productBarcode, lotNumber, quantity, and locationCode are required per row.",
                "Dates must use yyyy-MM-dd or true Excel date cells.",
                "Imported inbound documents remain in DRAFT status for review."
        ));
        autoSizeColumns(sheet, 8);
    }

    private void createPurchaseOrdersTemplate(final Workbook workbook) {
        final Sheet sheet = workbook.createSheet("purchase-orders");
        writeHeaderRow(workbook, sheet, "purchaseOrderReference", "centerCode", "warehouseCode", "supplierName", "supplierCode", "productBarcode", "requestedQuantity", "unitPrice", "note");
        writeExampleRow(sheet, 1, "PO-APR-001", "GANGNAM", "GN-01", "메인공급사", "SUP-001", "8801234567890", "120", "2100", "주말 행사 대비 발주");
        writeInstructionsSheet(workbook, "Purchase Orders Import", List.of(
                "Rows with the same purchaseOrderReference are grouped into one draft purchase order.",
                "centerCode and productBarcode must already exist in the system.",
                "warehouseCode is optional but must belong to the provided center when set.",
                "requestedQuantity must be greater than zero. unitPrice is optional."
        ));
        autoSizeColumns(sheet, 9);
    }

    private void writeHeaderRow(final Workbook workbook, final Sheet sheet, final String... headers) {
        final Row headerRow = sheet.createRow(HEADER_ROW_INDEX);
        final CellStyle headerStyle = workbook.createCellStyle();
        final Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setFillForegroundColor((short) 22);
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (int index = 0; index < headers.length; index++) {
            final Cell cell = headerRow.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeExampleRow(final Sheet sheet, final int rowIndex, final String... values) {
        final Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
    }

    private void writeInstructionsSheet(final Workbook workbook, final String title, final List<String> instructions) {
        final Sheet sheet = workbook.createSheet("instructions");
        final Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue(title);
        for (int index = 0; index < instructions.size(); index++) {
            final Row row = sheet.createRow(index + 2);
            row.createCell(0).setCellValue("- " + instructions.get(index));
        }
        autoSizeColumns(sheet, 1);
    }

    private void autoSizeColumns(final Sheet sheet, final int columnCount) {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private record InboundContext(Long inboundId, LocalDate inboundDate, String supplier) {
    }

    private record PurchaseOrderContext(
            PurchaseOrder purchaseOrder,
            String centerCode,
            String warehouseCode,
            String supplierName,
            String supplierCode
    ) {
    }
}
