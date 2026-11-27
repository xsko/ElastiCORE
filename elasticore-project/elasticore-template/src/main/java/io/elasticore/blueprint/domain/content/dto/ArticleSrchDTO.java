//ecd:-1664759905H20261127190213_V3.1
package io.elasticore.blueprint.domain.content.dto;

import io.elasticore.blueprint.domain.content.enums.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;
import java.time.*;
import io.elasticore.blueprint.domain.content.enums.*;


/**
 * ArticleSrchDTO
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
public  class ArticleSrchDTO  implements java.io.Serializable, SortableObject, PageableObject  {

	@Schema(description = "Field matches pattern. field:categoryName"  )
	private String categoryName;
	
	@Schema(description = "Field equals value. field:categoryId"  )
	private Long categoryId;
	
	@Schema(description = "Field matches pattern. field:title"  )
	private String title;
	
	@Schema(description = "Field equals value. field:slug"  )
	private String slug;
	
	@Schema(description = "Field is between two values (inclusive). field:publishDate"  , example="yyyy-MM-dd HH:mm:ss")
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private java.time.LocalDateTime publishDateFrom;
	
	@org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private java.time.LocalDateTime publishDateTo;
	
	@Schema(description = "Field is greater than or equal to value. field:viewCount"  )
	private Long viewCount;
	
	@Schema(description = "Field equals value. field:featured"  )
	private Boolean featured;
	
	@Schema(description = "Field matches any value in the given list. field:articleStatus"  , example="DRF: Draft | REV: Review | PUB: Published | ARC: Archived")
	private ArticleStatus articleStatus;
	
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
	
	
	private String sortCode;
	
	@Builder.Default
	private int pageNumber=0;
	
	@Builder.Default
	private int pageSize=30;

    private String sortColumn; // Column to sort by
    private Boolean sortAscending; // Sort order (true: ascending, false: descending)
}
