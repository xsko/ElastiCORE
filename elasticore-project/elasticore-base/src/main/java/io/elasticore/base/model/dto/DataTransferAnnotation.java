package io.elasticore.base.model.dto;

import io.elasticore.base.model.entity.EntityAnnotation;

/**
 * This interface defines various annotation names used in elastiCore modeling.
 */
public interface DataTransferAnnotation extends EntityAnnotation {



    String META_SEARCHABLE = "searchable";


    String META_SEARCH_RESULT = "searchResult";


    String META_SEARCHABLE_BYPASS = "searchBypass";



    String META_SEARCHABLE_PAGESIZE = "searchable.pageSize";


    String META_SEARCHABLE_PAGEABLE = "searchable.pageable";

    String META_EXTEND = "extend";

    String META_TEMPLATE = "template";

    /** The fields a DTO takes of what it copies: @only(note, deliveryDate). */
    String META_ONLY = "only";

    String[] META_SEARCHABLE_NAME = new String[]{"searchable.entity", "searchable"};


    String[] META_SEARCH_RESULT_NAME = new String[]{"searchResult.entity", "searchResult"};


    String[] META_JSON_INCLUDE = new String[]{"jsoninclude", "includejson"};


    String[] META_PROTO = new String[]{"proto", "protobug","grpc"};
    /**
     * pattern annotaion for DTO
     */
    String PATTERN = "pattern";

    String PATTERN_REGEX = "pattern.regexp";

    String PATTERN_MSG = "pattern.message";


    String[] READ_ONLY = new String[]{"readonly", "noinput" };


    String[] WRITE_ONLY = new String[]{"writeonly", "nooutput" };

    /** Meta annotation to enable JsonView feature on DTO */
    String META_JSONVIEW = "jsonview";

    /** Field annotation for specifying view visibility (c=create, u=update, r=read) */
    String VIEW = "view";

    /**
     * Internal marker for nested relation DTO fields auto-added to a main DTO from an
     * entity relation (e.g. {@code batch: BatchInfo @ManyToOne} -> {@code BatchInfoDTO batch}).
     * Such fields are populated by the framework on read, while writes use the flattened
     * FK id field (e.g. {@code batchBatchId}), so they are documented as READ_ONLY in the
     * OpenAPI schema. Documentation-only: runtime (de)serialization is not affected.
     */
    String META_AUTO_RELATION = "autoRelation";
}