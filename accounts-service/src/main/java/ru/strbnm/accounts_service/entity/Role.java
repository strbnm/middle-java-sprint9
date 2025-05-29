package ru.strbnm.accounts_service.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Table("roles")
public class Role {

    @Id
    private Integer id;

    @Column("role_name")
    private String roleName;

    private String description;

}
