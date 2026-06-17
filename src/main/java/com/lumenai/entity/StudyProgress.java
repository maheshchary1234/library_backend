package com.lumenai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "study_progress")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudyProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "study_hours")
    private Double studyHours;

    private Integer streak;
    private Double accuracy;
}
