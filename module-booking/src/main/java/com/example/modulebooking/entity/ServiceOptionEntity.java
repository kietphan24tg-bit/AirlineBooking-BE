package com.example.modulebooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "service_options")
@Getter
@Setter
@NoArgsConstructor
public class ServiceOptionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "service_id", nullable = false)
  private ServiceEntity service;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal price;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;
}
