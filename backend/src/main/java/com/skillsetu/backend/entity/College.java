package com.skillsetu.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "colleges")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class College extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(name = "established_year")
    private Integer establishedYear;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "college", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<User> tpos = new ArrayList<>();

    @OneToMany(mappedBy = "college", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<User> students = new ArrayList<>();


    @Column(name = "is_active")
    private Boolean isActive = true;
}
