//ecd:1082111755H20261104091202_V3.0
package io.elasticore.blueprint.domain.hr.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.*;
import org.hibernate.annotations.Parameter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import java.util.*;
import java.time.*;
import io.elasticore.blueprint.domain.hr.enums.ProjectStatus;
import io.elasticore.blueprint.domain.hr.entity.Employee;
import jakarta.persistence.Entity;


/**
 * Project
 *
 * Modify this code only as specified in the ElastiCORE guidelines
 * to avoid regeneration conflicts.
 *
 * Generated and managed by ElastiCORE.
 */

@Entity
@jakarta.persistence.Table(name="hr_project")
@org.hibernate.annotations.DynamicUpdate
@Getter
@Setter
@NoArgsConstructor

@EntityListeners(AuditingEntityListener.class)
public  class Project  implements java.io.Serializable  {

	public Project(String projectCode) {
	    this.projectCode = projectCode;
	}
	
	@Id
	@Column(name = "project_code", length = 20)
	private String projectCode;
	
	
	@Column(name = "name", nullable = false, length = 100)
	private String name;
	
	
	@Column(name = "start_date")
	private java.time.LocalDate startDate;
	
	
	@Column(name = "end_date")
	private java.time.LocalDate endDate;
	
	
	@Column(name = "budget", precision = 14, scale = 2)
	private java.math.BigDecimal budget;
	
	
	@Column(length = 3)
	@Convert(converter = ProjectStatus.EntityConverter.class)
	private ProjectStatus status;
	
	
	@ManyToOne
	private Employee lead;
	
	
}
