package com.stockops.service;

import com.stockops.entity.Center;
import com.stockops.repository.CenterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for Center management.
 *
 * @author StockOps Team
 * @since 2.0
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CenterService {

    private final CenterRepository centerRepository;

    public List<Center> findAll() {
        return centerRepository.findAll();
    }

    public Center findById(Long id) {
        return centerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Center not found: " + id));
    }

    public Center findByCode(String code) {
        return centerRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Center not found: " + code));
    }

    public Center create(Center center) {
        if (centerRepository.existsByCode(center.getCode())) {
            throw new RuntimeException("Center code already exists: " + center.getCode());
        }
        center.setStatus("ACTIVE");
        return centerRepository.save(center);
    }

    public Center update(Long id, Center center) {
        Center existing = findById(id);
        existing.setName(center.getName());
        existing.setAddress(center.getAddress());
        existing.setPhone(center.getPhone());
        existing.setStatus(center.getStatus());
        return centerRepository.save(existing);
    }

    public void delete(Long id) {
        Center center = findById(id);
        center.setStatus("CLOSED");
        centerRepository.save(center);
    }
}
