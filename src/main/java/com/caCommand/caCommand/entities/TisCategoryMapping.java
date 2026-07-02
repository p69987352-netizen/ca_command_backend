package com.caCommand.caCommand.entities;

import com.caCommand.caCommand.enums.CategoryType;
import com.caCommand.caCommand.enums.DocumentType;
import com.caCommand.caCommand.enums.IncomeCategory;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tis_category_mappings")
@Data
@lombok.EqualsAndHashCode(callSuper=false)
public class TisCategoryMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CategoryType categoryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IncomeCategory incomeCategory;

    @Column(nullable = false)
    private Boolean shouldAggregate = false;

    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private Integer confidenceWeight = 100;

    @Column(nullable = false)
    private Boolean enabled = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
