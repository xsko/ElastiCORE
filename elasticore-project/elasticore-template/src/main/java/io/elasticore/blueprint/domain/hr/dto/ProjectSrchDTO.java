//ecd:-1333030947H20261104091202_V3.0
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
import io.elasticore.blueprint.domain.hr.enums.*;


/**
 * ProjectSrchDTO
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
public  class ProjectSrchDTO  implements java.io.Serializable, SortableObject, PageableObject  {

	@Schema(description = "Field matches any value in the given list. field:status"  , example="PLN: Planned | ACT: Active | HLD: On Hold | CMP: Completed | CXL: Cancelled")
	private ProjectStatus status;
	
	@Schema(description = "Field equals value. field:leadEmpId"  )
	private String leadEmpId;
	
	@Schema(description = "Use 'like' if value has %, else 'equal' field:projectCode"  )
	private String projectCode;
	
	@Schema(description = "Field matches pattern. field:name"  )
	private String name;
	
	@Schema(description = "Field is between two values (inclusive). field:startDate"  , example="yyyy-MM-dd")
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
	private java.time.LocalDate startDateFrom;
	
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
	private java.time.LocalDate startDateTo;
	
	@Schema(description = "Field is between two values (inclusive). field:endDate"  , example="yyyy-MM-dd")
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
	private java.time.LocalDate endDateFrom;
	
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
	private java.time.LocalDate endDateTo;
	
	@Schema(description = "Field is between two values (inclusive). field:budget"  )
	private java.math.BigDecimal budgetFrom;
	
	private java.math.BigDecimal budgetTo;
	
	
	private String sortCode;
	
	@Builder.Default
	private int pageNumber=0;
	
	@Builder.Default
	private int pageSize=20;

    private String sortColumn; // Column to sort by
    private Boolean sortAscending; // Sort order (true: ascending, false: descending)
}
