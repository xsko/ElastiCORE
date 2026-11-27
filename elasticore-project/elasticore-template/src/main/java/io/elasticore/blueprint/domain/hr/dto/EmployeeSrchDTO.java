//ecd:791214707H20261127190213_V3.1
package io.elasticore.blueprint.domain.hr.dto;

import io.elasticore.blueprint.domain.hr.enums.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;
import java.time.*;



/**
 * EmployeeSrchDTO
 *
 * Modify this code only as specified in the ElastiCORE guidelines
 * to avoid regeneration conflicts.
 *
 * Generated and managed by ElastiCORE.
 */

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public  class EmployeeSrchDTO  implements java.io.Serializable, SortableObject, PageableObject  {

	@Schema(description = "Field equals value. field:deptCode"  )
	private String deptCode;
	
	@Schema(description = "Field matches pattern. field:deptName"  )
	private String deptName;
	
	@Schema(description = "Field matches pattern. field:firstName"  )
	private String firstName;
	
	@Schema(description = "Field matches pattern. field:lastName"  )
	private String lastName;
	
	@Schema(description = "Field equals value. field:email"  )
	private String email;
	
	@Schema(description = "Field matches value (case-insensitive) with identical content between start and end. field:phone"  )
	private String phone;
	
	@Schema(description = "Field is between two values (inclusive). field:hireDate"  , example="yyyy-MM-dd")
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
	private java.time.LocalDate hireDateFrom;
	
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
	private java.time.LocalDate hireDateTo;
	
	@Schema(description = "Field is between two values (inclusive). field:salary"  )
	private java.math.BigDecimal salaryFrom;
	
	private java.math.BigDecimal salaryTo;
	
	@Schema(description = "Field equals value. field:active"  )
	private Boolean active;
	
	@Schema(description = "Use 'like' if value has %, else 'equal' field:createdBy"  )
	private String createdBy;
	
	@Schema(description = "Field is between two values (inclusive). field:createDate"  , example="yyyy-MM-dd HH:mm:ss")
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private java.time.LocalDateTime createDateFrom;
	
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private java.time.LocalDateTime createDateTo;
	
	@Schema(description = "Use 'like' if value has %, else 'equal' field:lastModifiedBy"  )
	private String lastModifiedBy;
	
	@Schema(description = "Field is between two values (inclusive). field:lastModifiedDate"  , example="yyyy-MM-dd HH:mm:ss")
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private java.time.LocalDateTime lastModifiedDateFrom;
	
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private java.time.LocalDateTime lastModifiedDateTo;
	
	@Schema(description = "Field equals value. field:version"  )
	private Long version;
	
	
	private String sortCode;
	
	@Builder.Default
	private int pageNumber=0;
	
	@Builder.Default
	private int pageSize=25;

    private String sortColumn; // Column to sort by
    private Boolean sortAscending; // Sort order (true: ascending, false: descending)
}
