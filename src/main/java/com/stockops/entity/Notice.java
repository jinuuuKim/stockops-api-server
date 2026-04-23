package com.stockops.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import java.time.Instant;

/**
 * System notice entity for admin announcements.
 */
@Entity
@Table(name = "notices")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SQLRestriction("deleted = false")
public class Notice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NoticeType type = NoticeType.SYSTEM;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "notice_at")
    private Instant noticeAt;
}