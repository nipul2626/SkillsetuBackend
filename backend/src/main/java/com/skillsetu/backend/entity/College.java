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

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 500)
    private String address;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @OneToMany(mappedBy = "college", cascade = CascadeType.ALL)
    private List<User> users = new ArrayList<>();

    @Column(length = 200)
    private String location;

    private Integer establishedYear;

    @Column(length = 500)
    private String description;

    private Boolean isActive = true;

}
