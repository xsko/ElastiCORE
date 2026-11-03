//ecd:-122017607H20261103120735_V3.0
package io.elasticore.blueprint.domain.content.dto;

import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.EntityManager;

import java.util.*;
import java.util.stream.Collectors;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import io.elasticore.blueprint.domain.content.entity.*;
import io.elasticore.blueprint.domain.content.dto.*;
import io.elasticore.blueprint.domain.content.enums.*;

import io.elasticore.blueprint.domain.content.dto.*;
import io.elasticore.blueprint.domain.content.entity.*;
import io.elasticore.blueprint.domain.content.enums.*;
import io.elasticore.springboot3.mapper.MappingContext;
import io.elasticore.runtime.security.TransformPermissionChecker;

/**
 * Provides transformation utilities between DTOs and entities,
 * including conditional mapping, permission validation, and dynamic query support.
 *
 * Modify this code only as specified in the ElastiCORE guidelines
 * to avoid regeneration conflicts.
 *
 * Generated and managed by ElastiCORE.
 */


public class ContentMapper {

    private static TransformPermissionChecker permissionChecker;

    public static void setTransformPermissionChecker(TransformPermissionChecker checker) {
        permissionChecker = checker;
    }


    protected static void checkPermission(Object from, Object to) {
        if(permissionChecker !=null) {
            if( !permissionChecker.hasPermission(from, to)) {
                throw new PermissionDeniedDataAccessException(from.getClass().getName()+ " access error" ,new RuntimeException());
            }
        }
    }

    
    public static void mapping(ContentCategory from, ContentCategoryDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(ContentCategory from, ContentCategoryDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("children").checkEnable())
        setVal(from.getChildren(), to::setChildren, isSkipNull, e->toContentCategoryDTOList(e,c));
        if(c==null || c.fd("categoryId").checkEnable())
        setVal(from.getCategoryId(), to::setCategoryId, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("slug").checkEnable())
        setVal(from.getSlug(), to::setSlug, isSkipNull);
        if(c==null || c.fd("description").checkEnable())
        setVal(from.getDescription(), to::setDescription, isSkipNull);
        if(c==null || c.fd("sortOrder").checkEnable())
        setVal(from.getSortOrder(), to::setSortOrder, isSkipNull);
        if(c==null || c.fd("childCount").checkEnable())
        setVal(from.getChildCount(), to::setChildCount, isSkipNull);
        if(c==null || c.fd("parentName").checkEnable())
        setVal(from.getParentName(), to::setParentName, isSkipNull);
        if(isSkip("ContentCategory","ContentBase")) return;
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
    }
    
    
    public static void mapping(ContentCategory from, ContentCategoryDTO to){
        mapping(from,to,false);
    }
    public static void mapping(ContentCategory from, ContentCategoryDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static ContentCategoryDTO toDTO(ContentCategory from){
        return toDTO(from,MappingContext.withGuard(2,null));
    }
    public static ContentCategoryDTO toDTO(ContentCategory from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        ContentCategoryDTO to = new ContentCategoryDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<ContentCategoryDTO> toContentCategoryDTOList(List<ContentCategory> fromList){
        return toContentCategoryDTOList(fromList,(MappingContext) null);
    }
    public static List<ContentCategoryDTO> toContentCategoryDTOList(List<ContentCategory> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<ContentCategoryDTO> toContentCategoryDTOList(List<ContentCategory> fromList, BiFunction<ContentCategory, ContentCategoryDTO, ContentCategoryDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                ContentCategoryDTO to = toDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(Tag from, TagDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(Tag from, TagDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("tagId").checkEnable())
        setVal(from.getTagId(), to::setTagId, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("color").checkEnable())
        setVal(from.getColor(), to::setColor, isSkipNull);
    }
    
    
    public static void mapping(Tag from, TagDTO to){
        mapping(from,to,false);
    }
    public static void mapping(Tag from, TagDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static TagDTO toDTO(Tag from){
        return toDTO(from,MappingContext.withGuard(2,null));
    }
    public static TagDTO toDTO(Tag from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        TagDTO to = new TagDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<TagDTO> toTagDTOList(List<Tag> fromList){
        return toTagDTOList(fromList,(MappingContext) null);
    }
    public static List<TagDTO> toTagDTOList(List<Tag> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<TagDTO> toTagDTOList(List<Tag> fromList, BiFunction<Tag, TagDTO, TagDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                TagDTO to = toDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(Article from, ArticleDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(Article from, ArticleDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(hasValue(from.getCategory()))
            to.setCategoryName(from.getCategory().getName());
        if(hasValue(from.getCategory()))
            to.setCategorySlug(from.getCategory().getSlug());
        if(c==null || c.fd("attachments").checkEnable())
        setVal(from.getAttachments(), to::setAttachments, isSkipNull, e->toAttachmentDTOList(e,c));
        if(c==null || c.fd("articleId").checkEnable())
        setVal(from.getArticleId(), to::setArticleId, isSkipNull);
        if(c==null || c.fd("title").checkEnable())
        setVal(from.getTitle(), to::setTitle, isSkipNull);
        if(c==null || c.fd("slug").checkEnable())
        setVal(from.getSlug(), to::setSlug, isSkipNull);
        if(c==null || c.fd("summary").checkEnable())
        setVal(from.getSummary(), to::setSummary, isSkipNull);
        if(c==null || c.fd("publishDate").checkEnable())
        setVal(from.getPublishDate(), to::setPublishDate, isSkipNull);
        if(c==null || c.fd("viewCount").checkEnable())
        setVal(from.getViewCount(), to::setViewCount, isSkipNull);
        if(c==null || c.fd("featured").checkEnable())
        setVal(from.getFeatured(), to::setFeatured, isSkipNull);
        if(c==null || c.fd("articleStatus").checkEnable())
        setVal(from.getArticleStatus(), to::setArticleStatus, isSkipNull);
        if(c==null || c.fd("ratings").checkEnable())
        setVal(from.getRatings(), to::setRatings, isSkipNull);
        if(c==null || c.fd("labels").checkEnable())
        setVal(from.getLabels(), to::setLabels, isSkipNull);
        if(isSkip("Article","ContentBase")) return;
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
    }
    
    
    public static void mapping(Article from, ArticleDTO to){
        mapping(from,to,false);
    }
    public static void mapping(Article from, ArticleDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static ArticleDTO toDTO(Article from){
        return toDTO(from,MappingContext.withGuard(2,null));
    }
    public static ArticleDTO toDTO(Article from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        ArticleDTO to = new ArticleDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<ArticleDTO> toArticleDTOList(List<Article> fromList){
        return toArticleDTOList(fromList,(MappingContext) null);
    }
    public static List<ArticleDTO> toArticleDTOList(List<Article> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<ArticleDTO> toArticleDTOList(List<Article> fromList, BiFunction<Article, ArticleDTO, ArticleDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                ArticleDTO to = toDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(Comment from, CommentDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(Comment from, CommentDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(hasValue(from.getArticle()))
            to.setArticleTitle(from.getArticle().getTitle());
        if(c==null || c.fd("replies").checkEnable())
        setVal(from.getReplies(), to::setReplies, isSkipNull, e->toCommentDTOList(e,c));
        if(c==null || c.fd("commentId").checkEnable())
        setVal(from.getCommentId(), to::setCommentId, isSkipNull);
        if(c==null || c.fd("content").checkEnable())
        setVal(from.getContent(), to::setContent, isSkipNull);
        if(c==null || c.fd("authorName").checkEnable())
        setVal(from.getAuthorName(), to::setAuthorName, isSkipNull);
        if(c==null || c.fd("authorEmail").checkEnable())
        setVal(from.getAuthorEmail(), to::setAuthorEmail, isSkipNull);
        if(c==null || c.fd("approved").checkEnable())
        setVal(from.getApproved(), to::setApproved, isSkipNull);
        if(isSkip("Comment","ContentBase")) return;
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
    }
    
    
    public static void mapping(Comment from, CommentDTO to){
        mapping(from,to,false);
    }
    public static void mapping(Comment from, CommentDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static CommentDTO toDTO(Comment from){
        return toDTO(from,MappingContext.withGuard(2,null));
    }
    public static CommentDTO toDTO(Comment from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        CommentDTO to = new CommentDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<CommentDTO> toCommentDTOList(List<Comment> fromList){
        return toCommentDTOList(fromList,(MappingContext) null);
    }
    public static List<CommentDTO> toCommentDTOList(List<Comment> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<CommentDTO> toCommentDTOList(List<Comment> fromList, BiFunction<Comment, CommentDTO, CommentDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                CommentDTO to = toDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(Attachment from, AttachmentDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(Attachment from, AttachmentDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(hasValue(from.getArticle()))
            to.setArticleId(from.getArticle().getArticleId());
        if(c==null || c.fd("attachId").checkEnable())
        setVal(from.getAttachId(), to::setAttachId, isSkipNull);
        if(c==null || c.fd("fileName").checkEnable())
        setVal(from.getFileName(), to::setFileName, isSkipNull);
        if(c==null || c.fd("fileSize").checkEnable())
        setVal(from.getFileSize(), to::setFileSize, isSkipNull);
        if(c==null || c.fd("contentType").checkEnable())
        setVal(from.getContentType(), to::setContentType, isSkipNull);
        if(c==null || c.fd("storageKey").checkEnable())
        setVal(from.getStorageKey(), to::setStorageKey, isSkipNull);
    }
    
    
    public static void mapping(Attachment from, AttachmentDTO to){
        mapping(from,to,false);
    }
    public static void mapping(Attachment from, AttachmentDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static AttachmentDTO toDTO(Attachment from){
        return toDTO(from,MappingContext.withGuard(2,null));
    }
    public static AttachmentDTO toDTO(Attachment from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        AttachmentDTO to = new AttachmentDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<AttachmentDTO> toAttachmentDTOList(List<Attachment> fromList){
        return toAttachmentDTOList(fromList,(MappingContext) null);
    }
    public static List<AttachmentDTO> toAttachmentDTOList(List<Attachment> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<AttachmentDTO> toAttachmentDTOList(List<Attachment> fromList, BiFunction<Attachment, AttachmentDTO, AttachmentDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                AttachmentDTO to = toDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(Bookmark from, BookmarkDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(Bookmark from, BookmarkDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("article").checkEnable())
        setVal(from.getArticle(), to::setArticle, isSkipNull, e->toDTO(e,c));
        if(hasValue(from.getArticle()))
            to.setArticleArticleId(from.getArticle().getArticleId());
        if(c==null || c.fd("bookmarkId").checkEnable())
        setVal(from.getBookmarkId(), to::setBookmarkId, isSkipNull);
        if(c==null || c.fd("memo").checkEnable())
        setVal(from.getMemo(), to::setMemo, isSkipNull);
    }
    
    
    public static void mapping(Bookmark from, BookmarkDTO to){
        mapping(from,to,false);
    }
    public static void mapping(Bookmark from, BookmarkDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static BookmarkDTO toDTO(Bookmark from){
        return toDTO(from,MappingContext.withGuard(2,null));
    }
    public static BookmarkDTO toDTO(Bookmark from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        BookmarkDTO to = new BookmarkDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<BookmarkDTO> toBookmarkDTOList(List<Bookmark> fromList){
        return toBookmarkDTOList(fromList,(MappingContext) null);
    }
    public static List<BookmarkDTO> toBookmarkDTOList(List<Bookmark> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<BookmarkDTO> toBookmarkDTOList(List<Bookmark> fromList, BiFunction<Bookmark, BookmarkDTO, BookmarkDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                BookmarkDTO to = toDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(ArticleDTO from, Article to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(ArticleDTO from, Article to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("articleId").checkEnable())
        setVal(from.getArticleId(), to::setArticleId, isSkipNull);
        if(c==null || c.fd("title").checkEnable())
        setVal(from.getTitle(), to::setTitle, isSkipNull);
        if(c==null || c.fd("slug").checkEnable())
        setVal(from.getSlug(), to::setSlug, isSkipNull);
        if(c==null || c.fd("summary").checkEnable())
        setVal(from.getSummary(), to::setSummary, isSkipNull);
        if(c==null || c.fd("publishDate").checkEnable())
        setVal(from.getPublishDate(), to::setPublishDate, isSkipNull);
        if(c==null || c.fd("viewCount").checkEnable())
        setVal(from.getViewCount(), to::setViewCount, isSkipNull);
        if(c==null || c.fd("featured").checkEnable())
        setVal(from.getFeatured(), to::setFeatured, isSkipNull);
        if(c==null || c.fd("articleStatus").checkEnable())
        setVal(from.getArticleStatus(), to::setArticleStatus, isSkipNull);
        if(c==null || c.fd("ratings").checkEnable())
        setVal(from.getRatings(), to::setRatings, isSkipNull);
        if(c==null || c.fd("labels").checkEnable())
        setVal(from.getLabels(), to::setLabels, isSkipNull);
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
        if(hasValue(from.getCategoryName()) && hasValue(to.getCategory()))
            to.getCategory().setName(from.getCategoryName());
        if(hasValue(from.getCategorySlug()) && hasValue(to.getCategory()))
            to.getCategory().setSlug(from.getCategorySlug());
    }
    
    
    public static void mapping(ArticleDTO from, Article to){
        mapping(from,to,false);
    }
    public static void mapping(ArticleDTO from, Article to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(ArticleDTO from, Article to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(ArticleDTO from, Article to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(ArticleDTO from, Article to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(ArticleDTO from, Article to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("articleId").checkEnable())
        setVal(from.getArticleId(), to::setArticleId, isSkipNull);
        if(c==null || c.fd("title").checkEnable())
        setVal(from.getTitle(), to::setTitle, isSkipNull);
        if(c==null || c.fd("slug").checkEnable())
        setVal(from.getSlug(), to::setSlug, isSkipNull);
        if(c==null || c.fd("summary").checkEnable())
        setVal(from.getSummary(), to::setSummary, isSkipNull);
        if(c==null || c.fd("publishDate").checkEnable())
        setVal(from.getPublishDate(), to::setPublishDate, isSkipNull);
        if(c==null || c.fd("viewCount").checkEnable())
        setVal(from.getViewCount(), to::setViewCount, isSkipNull);
        if(c==null || c.fd("featured").checkEnable())
        setVal(from.getFeatured(), to::setFeatured, isSkipNull);
        if(c==null || c.fd("articleStatus").checkEnable())
        setVal(from.getArticleStatus(), to::setArticleStatus, isSkipNull);
        if(c==null || c.fd("ratings").checkEnable())
        setVal(from.getRatings(), to::setRatings, isSkipNull);
        if(c==null || c.fd("labels").checkEnable())
        setVal(from.getLabels(), to::setLabels, isSkipNull);
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
        if(hasValue(from.getCategoryName()) && hasValue(to.getCategory()))
            to.getCategory().setName(from.getCategoryName());
        if(hasValue(from.getCategorySlug()) && hasValue(to.getCategory()))
            to.getCategory().setSlug(from.getCategorySlug());
    }
    
    
    public static Article toEntity(ArticleDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static Article toEntity(ArticleDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Article to = new Article();
        mapping(from,to,c);
        return to;
    }
    
    
    public static Article toEntity(ArticleDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static Article toEntity(ArticleDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Article to = new Article();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<Article> toArticleList(List<ArticleDTO> fromList){
        return toArticleList(fromList,(MappingContext) null);
    }
    public static List<Article> toArticleList(List<ArticleDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<Article> toArticleList(List<ArticleDTO> fromList, EntityManager em){
        return toArticleList(fromList,(MappingContext) null,em);
    }
    public static List<Article> toArticleList(List<ArticleDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<Article> toArticleList(List<ArticleDTO> fromList, BiFunction<ArticleDTO, Article, Article> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                Article to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(ContentCategoryDTO from, ContentCategory to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(ContentCategoryDTO from, ContentCategory to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("categoryId").checkEnable())
        setVal(from.getCategoryId(), to::setCategoryId, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("slug").checkEnable())
        setVal(from.getSlug(), to::setSlug, isSkipNull);
        if(c==null || c.fd("description").checkEnable())
        setVal(from.getDescription(), to::setDescription, isSkipNull);
        if(c==null || c.fd("sortOrder").checkEnable())
        setVal(from.getSortOrder(), to::setSortOrder, isSkipNull);
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
    }
    
    
    public static void mapping(ContentCategoryDTO from, ContentCategory to){
        mapping(from,to,false);
    }
    public static void mapping(ContentCategoryDTO from, ContentCategory to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(ContentCategoryDTO from, ContentCategory to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(ContentCategoryDTO from, ContentCategory to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(ContentCategoryDTO from, ContentCategory to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(ContentCategoryDTO from, ContentCategory to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("categoryId").checkEnable())
        setVal(from.getCategoryId(), to::setCategoryId, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("slug").checkEnable())
        setVal(from.getSlug(), to::setSlug, isSkipNull);
        if(c==null || c.fd("description").checkEnable())
        setVal(from.getDescription(), to::setDescription, isSkipNull);
        if(c==null || c.fd("sortOrder").checkEnable())
        setVal(from.getSortOrder(), to::setSortOrder, isSkipNull);
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
    }
    
    
    public static ContentCategory toEntity(ContentCategoryDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static ContentCategory toEntity(ContentCategoryDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        ContentCategory to = new ContentCategory();
        mapping(from,to,c);
        return to;
    }
    
    
    public static ContentCategory toEntity(ContentCategoryDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static ContentCategory toEntity(ContentCategoryDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        ContentCategory to = new ContentCategory();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<ContentCategory> toContentCategoryList(List<ContentCategoryDTO> fromList){
        return toContentCategoryList(fromList,(MappingContext) null);
    }
    public static List<ContentCategory> toContentCategoryList(List<ContentCategoryDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<ContentCategory> toContentCategoryList(List<ContentCategoryDTO> fromList, EntityManager em){
        return toContentCategoryList(fromList,(MappingContext) null,em);
    }
    public static List<ContentCategory> toContentCategoryList(List<ContentCategoryDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<ContentCategory> toContentCategoryList(List<ContentCategoryDTO> fromList, BiFunction<ContentCategoryDTO, ContentCategory, ContentCategory> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                ContentCategory to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(CommentDTO from, Comment to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(CommentDTO from, Comment to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("commentId").checkEnable())
        setVal(from.getCommentId(), to::setCommentId, isSkipNull);
        if(c==null || c.fd("content").checkEnable())
        setVal(from.getContent(), to::setContent, isSkipNull);
        if(c==null || c.fd("authorName").checkEnable())
        setVal(from.getAuthorName(), to::setAuthorName, isSkipNull);
        if(c==null || c.fd("authorEmail").checkEnable())
        setVal(from.getAuthorEmail(), to::setAuthorEmail, isSkipNull);
        if(c==null || c.fd("approved").checkEnable())
        setVal(from.getApproved(), to::setApproved, isSkipNull);
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
        if(hasValue(from.getArticleTitle()) && hasValue(to.getArticle()))
            to.getArticle().setTitle(from.getArticleTitle());
    }
    
    
    public static void mapping(CommentDTO from, Comment to){
        mapping(from,to,false);
    }
    public static void mapping(CommentDTO from, Comment to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(CommentDTO from, Comment to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(CommentDTO from, Comment to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(CommentDTO from, Comment to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(CommentDTO from, Comment to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("commentId").checkEnable())
        setVal(from.getCommentId(), to::setCommentId, isSkipNull);
        if(c==null || c.fd("content").checkEnable())
        setVal(from.getContent(), to::setContent, isSkipNull);
        if(c==null || c.fd("authorName").checkEnable())
        setVal(from.getAuthorName(), to::setAuthorName, isSkipNull);
        if(c==null || c.fd("authorEmail").checkEnable())
        setVal(from.getAuthorEmail(), to::setAuthorEmail, isSkipNull);
        if(c==null || c.fd("approved").checkEnable())
        setVal(from.getApproved(), to::setApproved, isSkipNull);
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
        if(hasValue(from.getArticleTitle()) && hasValue(to.getArticle()))
            to.getArticle().setTitle(from.getArticleTitle());
    }
    
    
    public static Comment toEntity(CommentDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static Comment toEntity(CommentDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Comment to = new Comment();
        mapping(from,to,c);
        return to;
    }
    
    
    public static Comment toEntity(CommentDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static Comment toEntity(CommentDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Comment to = new Comment();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<Comment> toCommentList(List<CommentDTO> fromList){
        return toCommentList(fromList,(MappingContext) null);
    }
    public static List<Comment> toCommentList(List<CommentDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<Comment> toCommentList(List<CommentDTO> fromList, EntityManager em){
        return toCommentList(fromList,(MappingContext) null,em);
    }
    public static List<Comment> toCommentList(List<CommentDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<Comment> toCommentList(List<CommentDTO> fromList, BiFunction<CommentDTO, Comment, Comment> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                Comment to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(AttachmentDTO from, Attachment to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(AttachmentDTO from, Attachment to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("attachId").checkEnable())
        setVal(from.getAttachId(), to::setAttachId, isSkipNull);
        if(c==null || c.fd("fileName").checkEnable())
        setVal(from.getFileName(), to::setFileName, isSkipNull);
        if(c==null || c.fd("fileSize").checkEnable())
        setVal(from.getFileSize(), to::setFileSize, isSkipNull);
        if(c==null || c.fd("contentType").checkEnable())
        setVal(from.getContentType(), to::setContentType, isSkipNull);
        if(c==null || c.fd("storageKey").checkEnable())
        setVal(from.getStorageKey(), to::setStorageKey, isSkipNull);
        
        
        if(hasValue(from.getArticleId())){
            Article t = new Article();
            t.setArticleId(from.getArticleId());
            to.setArticle(t);
        }
        else if(!isSkipNull){
            to.setArticle(null);
        }
    }
    
    
    public static void mapping(AttachmentDTO from, Attachment to){
        mapping(from,to,false);
    }
    public static void mapping(AttachmentDTO from, Attachment to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(AttachmentDTO from, Attachment to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(AttachmentDTO from, Attachment to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(AttachmentDTO from, Attachment to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(AttachmentDTO from, Attachment to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("attachId").checkEnable())
        setVal(from.getAttachId(), to::setAttachId, isSkipNull);
        if(c==null || c.fd("fileName").checkEnable())
        setVal(from.getFileName(), to::setFileName, isSkipNull);
        if(c==null || c.fd("fileSize").checkEnable())
        setVal(from.getFileSize(), to::setFileSize, isSkipNull);
        if(c==null || c.fd("contentType").checkEnable())
        setVal(from.getContentType(), to::setContentType, isSkipNull);
        if(c==null || c.fd("storageKey").checkEnable())
        setVal(from.getStorageKey(), to::setStorageKey, isSkipNull);
        
        
        if(hasValue(from.getArticleId())){
            if(em != null){
                to.setArticle(em.getReference(Article.class, from.getArticleId()));
            }
            else{
                Article t = new Article();
                t.setArticleId(from.getArticleId());
                to.setArticle(t);
            }
        }
        else if(!isSkipNull){
            to.setArticle(null);
        }
    }
    
    
    public static Attachment toEntity(AttachmentDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static Attachment toEntity(AttachmentDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Attachment to = new Attachment();
        mapping(from,to,c);
        return to;
    }
    
    
    public static Attachment toEntity(AttachmentDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static Attachment toEntity(AttachmentDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Attachment to = new Attachment();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<Attachment> toAttachmentList(List<AttachmentDTO> fromList){
        return toAttachmentList(fromList,(MappingContext) null);
    }
    public static List<Attachment> toAttachmentList(List<AttachmentDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<Attachment> toAttachmentList(List<AttachmentDTO> fromList, EntityManager em){
        return toAttachmentList(fromList,(MappingContext) null,em);
    }
    public static List<Attachment> toAttachmentList(List<AttachmentDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<Attachment> toAttachmentList(List<AttachmentDTO> fromList, BiFunction<AttachmentDTO, Attachment, Attachment> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                Attachment to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(TagDTO from, Tag to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(TagDTO from, Tag to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("tagId").checkEnable())
        setVal(from.getTagId(), to::setTagId, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("color").checkEnable())
        setVal(from.getColor(), to::setColor, isSkipNull);
    }
    
    
    public static void mapping(TagDTO from, Tag to){
        mapping(from,to,false);
    }
    public static void mapping(TagDTO from, Tag to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(TagDTO from, Tag to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(TagDTO from, Tag to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(TagDTO from, Tag to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(TagDTO from, Tag to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("tagId").checkEnable())
        setVal(from.getTagId(), to::setTagId, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("color").checkEnable())
        setVal(from.getColor(), to::setColor, isSkipNull);
    }
    
    
    public static Tag toEntity(TagDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static Tag toEntity(TagDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Tag to = new Tag();
        mapping(from,to,c);
        return to;
    }
    
    
    public static Tag toEntity(TagDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static Tag toEntity(TagDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Tag to = new Tag();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<Tag> toTagList(List<TagDTO> fromList){
        return toTagList(fromList,(MappingContext) null);
    }
    public static List<Tag> toTagList(List<TagDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<Tag> toTagList(List<TagDTO> fromList, EntityManager em){
        return toTagList(fromList,(MappingContext) null,em);
    }
    public static List<Tag> toTagList(List<TagDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<Tag> toTagList(List<TagDTO> fromList, BiFunction<TagDTO, Tag, Tag> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                Tag to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(BookmarkDTO from, Bookmark to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(BookmarkDTO from, Bookmark to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("bookmarkId").checkEnable())
        setVal(from.getBookmarkId(), to::setBookmarkId, isSkipNull);
        if(c==null || c.fd("memo").checkEnable())
        setVal(from.getMemo(), to::setMemo, isSkipNull);
        
        
        if(hasValue(from.getArticleArticleId())){
            Article t = new Article();
            t.setArticleId(from.getArticleArticleId());
            to.setArticle(t);
        }
    }
    
    
    public static void mapping(BookmarkDTO from, Bookmark to){
        mapping(from,to,false);
    }
    public static void mapping(BookmarkDTO from, Bookmark to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(BookmarkDTO from, Bookmark to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(BookmarkDTO from, Bookmark to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(BookmarkDTO from, Bookmark to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(BookmarkDTO from, Bookmark to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("bookmarkId").checkEnable())
        setVal(from.getBookmarkId(), to::setBookmarkId, isSkipNull);
        if(c==null || c.fd("memo").checkEnable())
        setVal(from.getMemo(), to::setMemo, isSkipNull);
        
        
        if(hasValue(from.getArticleArticleId())){
            if(em != null){
                to.setArticle(em.getReference(Article.class, from.getArticleArticleId()));
            }
            else{
                Article t = new Article();
                t.setArticleId(from.getArticleArticleId());
                to.setArticle(t);
            }
        }
    }
    
    
    public static Bookmark toEntity(BookmarkDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static Bookmark toEntity(BookmarkDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Bookmark to = new Bookmark();
        mapping(from,to,c);
        return to;
    }
    
    
    public static Bookmark toEntity(BookmarkDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static Bookmark toEntity(BookmarkDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Bookmark to = new Bookmark();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<Bookmark> toBookmarkList(List<BookmarkDTO> fromList){
        return toBookmarkList(fromList,(MappingContext) null);
    }
    public static List<Bookmark> toBookmarkList(List<BookmarkDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<Bookmark> toBookmarkList(List<BookmarkDTO> fromList, EntityManager em){
        return toBookmarkList(fromList,(MappingContext) null,em);
    }
    public static List<Bookmark> toBookmarkList(List<BookmarkDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->ContentMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<Bookmark> toBookmarkList(List<BookmarkDTO> fromList, BiFunction<BookmarkDTO, Bookmark, Bookmark> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                Bookmark to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static Specification<Article> toSpec(ArticleSrchDTO searchDTO){
        Specification<Article> b = (r, q, c) -> c.conjunction();
        return toSpec(searchDTO, b);
    }
    
    
    public static Specification<Article> toSpec(ArticleSrchDTO searchDTO, Specification<Article> sp){
        String categoryName = searchDTO.getCategoryName();
        if(hasValue(categoryName)){
            sp = sp.and((r,q,c) -> c.like(r.get("category").get("name"),"%" +trim(categoryName)+ "%"));
        }
        Long categoryId = searchDTO.getCategoryId();
        if(hasValue(categoryId)){
            sp = sp.and((r,q,c) -> c.equal(r.get("category").get("categoryId"),categoryId));
        }
        String title = searchDTO.getTitle();
        if(hasValue(title)){
            sp = sp.and((r,q,c) -> c.like(r.get("title"),"%" +trim(title)+ "%"));
        }
        String slug = searchDTO.getSlug();
        if(hasValue(slug)){
            sp = sp.and((r,q,c) -> c.equal(r.get("slug"),slug));
        }
        java.time.LocalDateTime publishDateFrom = searchDTO.getPublishDateFrom();
        java.time.LocalDateTime publishDateTo = searchDTO.getPublishDateTo();
        if(hasValue(publishDateFrom) && hasValue(publishDateTo)){
            sp = sp.and((r,q,c) -> c.between(r.get("publishDate"),publishDateFrom,publishDateTo));
        }
        else if(hasValue(publishDateFrom)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("publishDate"),publishDateFrom));
        }
        else if(hasValue(publishDateTo)){
            sp = sp.and((r,q,c) -> c.lessThanOrEqualTo(r.get("publishDate"),publishDateTo));
        }
        Long viewCount = searchDTO.getViewCount();
        if(hasValue(viewCount)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("viewCount"),viewCount));
        }
        Boolean featured = searchDTO.getFeatured();
        if(hasValue(featured)){
            sp = sp.and((r,q,c) -> c.equal(r.get("featured"),featured));
        }
        ArticleStatus articleStatus = searchDTO.getArticleStatus();
        if(hasValue(articleStatus)){
            sp = sp.and((r, q, c) -> r.get("articleStatus").in(articleStatus));
        }
        return sp;
    }
    
    
    public static Specification<ContentCategory> toSpec(ContentCategorySrchDTO searchDTO){
        Specification<ContentCategory> b = (r, q, c) -> c.conjunction();
        return toSpec(searchDTO, b);
    }
    
    
    public static Specification<ContentCategory> toSpec(ContentCategorySrchDTO searchDTO, Specification<ContentCategory> sp){
        Long parentCategoryId = searchDTO.getParentCategoryId();
        if(hasValue(parentCategoryId)){
            sp = sp.and((r,q,c) -> c.equal(r.get("parent").get("categoryId"),parentCategoryId));
        }
        Long categoryId = searchDTO.getCategoryId();
        if(hasValue(categoryId)){
            sp = sp.and((r,q,c) -> c.equal(r.get("categoryId"),categoryId));
        }
        String name = searchDTO.getName();
        if(hasValue(name)){
            sp = sp.and((r,q,c) -> c.like(r.get("name"),"%" +trim(name)+ "%"));
        }
        String slug = searchDTO.getSlug();
        if(hasValue(slug)){
            sp = sp.and((r,q,c) -> c.equal(r.get("slug"),slug));
        }
        sp=setSpec(sp, "description", searchDTO.getDescription());
        Integer sortOrder = searchDTO.getSortOrder();
        if(hasValue(sortOrder)){
            sp = sp.and((r,q,c) -> c.equal(r.get("sortOrder"),sortOrder));
        }
        Long childCount = searchDTO.getChildCount();
        if(hasValue(childCount)){
            sp = sp.and((r,q,c) -> c.equal(r.get("childCount"),childCount));
        }
        sp=setSpec(sp, "parentName", searchDTO.getParentName());
        sp=setSpec(sp, "createdBy", searchDTO.getCreatedBy());
        java.time.LocalDateTime createDateFrom = searchDTO.getCreateDateFrom();
        java.time.LocalDateTime createDateTo = searchDTO.getCreateDateTo();
        if(hasValue(createDateFrom) && hasValue(createDateTo)){
            sp = sp.and((r,q,c) -> c.between(r.get("createDate"),createDateFrom,createDateTo));
        }
        else if(hasValue(createDateFrom)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("createDate"),createDateFrom));
        }
        else if(hasValue(createDateTo)){
            sp = sp.and((r,q,c) -> c.lessThanOrEqualTo(r.get("createDate"),createDateTo));
        }
        sp=setSpec(sp, "lastModifiedBy", searchDTO.getLastModifiedBy());
        java.time.LocalDateTime lastModifiedDateFrom = searchDTO.getLastModifiedDateFrom();
        java.time.LocalDateTime lastModifiedDateTo = searchDTO.getLastModifiedDateTo();
        if(hasValue(lastModifiedDateFrom) && hasValue(lastModifiedDateTo)){
            sp = sp.and((r,q,c) -> c.between(r.get("lastModifiedDate"),lastModifiedDateFrom,lastModifiedDateTo));
        }
        else if(hasValue(lastModifiedDateFrom)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("lastModifiedDate"),lastModifiedDateFrom));
        }
        else if(hasValue(lastModifiedDateTo)){
            sp = sp.and((r,q,c) -> c.lessThanOrEqualTo(r.get("lastModifiedDate"),lastModifiedDateTo));
        }
        return sp;
    }
    
    
    public static Specification<Tag> toSpec(TagSrchDTO searchDTO){
        Specification<Tag> b = (r, q, c) -> c.conjunction();
        return toSpec(searchDTO, b);
    }
    
    
    public static Specification<Tag> toSpec(TagSrchDTO searchDTO, Specification<Tag> sp){
        Long tagId = searchDTO.getTagId();
        if(hasValue(tagId)){
            sp = sp.and((r,q,c) -> c.equal(r.get("tagId"),tagId));
        }
        String name = searchDTO.getName();
        if(hasValue(name)){
            sp = sp.and((r,q,c) -> c.like(r.get("name"),"%" +trim(name)+ "%"));
        }
        sp=setSpec(sp, "color", searchDTO.getColor());
        return sp;
    }
    
    
    public static Specification<Comment> toSpec(CommentSrchDTO searchDTO){
        Specification<Comment> b = (r, q, c) -> c.conjunction();
        return toSpec(searchDTO, b);
    }
    
    
    public static Specification<Comment> toSpec(CommentSrchDTO searchDTO, Specification<Comment> sp){
        String articleArticleId = searchDTO.getArticleArticleId();
        if(hasValue(articleArticleId)){
            sp = sp.and((r,q,c) -> c.equal(r.get("article").get("articleId"),articleArticleId));
        }
        Long commentId = searchDTO.getCommentId();
        if(hasValue(commentId)){
            sp = sp.and((r,q,c) -> c.equal(r.get("commentId"),commentId));
        }
        sp=setSpec(sp, "content", searchDTO.getContent());
        String authorName = searchDTO.getAuthorName();
        if(hasValue(authorName)){
            sp = sp.and((r,q,c) -> c.like(r.get("authorName"),"%" +trim(authorName)+ "%"));
        }
        String authorEmail = searchDTO.getAuthorEmail();
        if(hasValue(authorEmail)){
            sp = sp.and((r,q,c) -> c.equal(r.get("authorEmail"),authorEmail));
        }
        Boolean approved = searchDTO.getApproved();
        if(hasValue(approved)){
            sp = sp.and((r,q,c) -> c.equal(r.get("approved"),approved));
        }
        sp=setSpec(sp, "createdBy", searchDTO.getCreatedBy());
        java.time.LocalDateTime createDateFrom = searchDTO.getCreateDateFrom();
        java.time.LocalDateTime createDateTo = searchDTO.getCreateDateTo();
        if(hasValue(createDateFrom) && hasValue(createDateTo)){
            sp = sp.and((r,q,c) -> c.between(r.get("createDate"),createDateFrom,createDateTo));
        }
        else if(hasValue(createDateFrom)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("createDate"),createDateFrom));
        }
        else if(hasValue(createDateTo)){
            sp = sp.and((r,q,c) -> c.lessThanOrEqualTo(r.get("createDate"),createDateTo));
        }
        sp=setSpec(sp, "lastModifiedBy", searchDTO.getLastModifiedBy());
        java.time.LocalDateTime lastModifiedDateFrom = searchDTO.getLastModifiedDateFrom();
        java.time.LocalDateTime lastModifiedDateTo = searchDTO.getLastModifiedDateTo();
        if(hasValue(lastModifiedDateFrom) && hasValue(lastModifiedDateTo)){
            sp = sp.and((r,q,c) -> c.between(r.get("lastModifiedDate"),lastModifiedDateFrom,lastModifiedDateTo));
        }
        else if(hasValue(lastModifiedDateFrom)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("lastModifiedDate"),lastModifiedDateFrom));
        }
        else if(hasValue(lastModifiedDateTo)){
            sp = sp.and((r,q,c) -> c.lessThanOrEqualTo(r.get("lastModifiedDate"),lastModifiedDateTo));
        }
        return sp;
    }
    
    
    public static Specification<Attachment> toSpec(AttachmentSrchDTO searchDTO){
        Specification<Attachment> b = (r, q, c) -> c.conjunction();
        return toSpec(searchDTO, b);
    }
    
    
    public static Specification<Attachment> toSpec(AttachmentSrchDTO searchDTO, Specification<Attachment> sp){
        String articleArticleId = searchDTO.getArticleArticleId();
        if(hasValue(articleArticleId)){
            sp = sp.and((r,q,c) -> c.equal(r.get("article").get("articleId"),articleArticleId));
        }
        return sp;
    }
    
    
    public static Specification<Bookmark> toSpec(BookmarkSrchDTO searchDTO){
        Specification<Bookmark> b = (r, q, c) -> c.conjunction();
        return toSpec(searchDTO, b);
    }
    
    
    public static Specification<Bookmark> toSpec(BookmarkSrchDTO searchDTO, Specification<Bookmark> sp){
        String articleArticleId = searchDTO.getArticleArticleId();
        if(hasValue(articleArticleId)){
            sp = sp.and((r,q,c) -> c.equal(r.get("article").get("articleId"),articleArticleId));
        }
        return sp;
    }
    

    protected static Specification setSpec(Specification sp, String fieldName, String value) {
        if(hasValue(value)){
            if(value.startsWith("%") || value.endsWith("%"))
                return sp.and((r,q,c) -> c.like(r.get(fieldName),value));
            else
                return sp.and((r,q,c) -> c.equal(r.get(fieldName),value));
        }
        return sp;
    }

    private static boolean hasValue(String val) {
        if(val != null && !val.isBlank())
            return true;

        return false;
    }

    private static boolean hasValue(java.time.LocalDateTime dateTime) {
        if(dateTime !=null)
            return true;

        return false;
    }

    private static boolean hasValue(List val) {
            if(val != null && val.size()>0)
                return true;

            return false;
    }

    private static boolean hasValue(Object val) {
        if(val != null )
            return true;

        return false;
    }

    private static String trim(String val) {
        if(val != null )
            return val.trim();

        return val;
    }

    private static Object trim(Object val) {
        return val;
    }

    private static boolean isSkip(String fromClassName, String refClassName) {
        return false;
    }

    public static void setVal(String value, Consumer<String> setter, boolean isSkipNull) {
        if ((value != null && !value.isEmpty())  || !isSkipNull) {
            Optional.ofNullable(value).ifPresent(setter);
        }
    }

    public static <T> void setVal(T value, Consumer<T> setter, boolean isSkipNull) {
        if (value != null || !isSkipNull) {
            Optional.ofNullable(value).ifPresent(setter);
        }
    }


    public static <T, R> void setVal(T value, Consumer<R> setter, boolean isSkipNull, Function<T, R> mapper) {


        if (value != null || !isSkipNull) {
            Optional.ofNullable(value).map(mapper).ifPresent(setter);
        }
    }
}
