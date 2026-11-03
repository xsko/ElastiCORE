//ecd:777874077H20261103104925_V3.0
package io.elasticore.blueprint.domain.hr.dto;

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
import io.elasticore.blueprint.domain.hr.entity.*;
import io.elasticore.blueprint.domain.hr.dto.*;
import io.elasticore.blueprint.domain.hr.enums.*;

import io.elasticore.blueprint.domain.hr.entity.*;
import io.elasticore.blueprint.domain.hr.enums.*;
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


public class HrMapper {

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

    
    public static void mapping(Department from, DepartmentDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(Department from, DepartmentDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("deptId").checkEnable())
        setVal(from.getDeptId(), to::setDeptId, isSkipNull);
        if(c==null || c.fd("code").checkEnable())
        setVal(from.getCode(), to::setCode, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("nameInsensitive").checkEnable())
        setVal(from.getNameInsensitive(), to::setNameInsensitive, isSkipNull);
        if(c==null || c.fd("description").checkEnable())
        setVal(from.getDescription(), to::setDescription, isSkipNull);
        if(c==null || c.fd("budget").checkEnable())
        setVal(from.getBudget(), to::setBudget, isSkipNull);
        if(c==null || c.fd("headCount").checkEnable())
        setVal(from.getHeadCount(), to::setHeadCount, isSkipNull);
        if(c==null || c.fd("maxCapacity").checkEnable())
        setVal(from.getMaxCapacity(), to::setMaxCapacity, isSkipNull);
        if(c==null || c.fd("floorNumber").checkEnable())
        setVal(from.getFloorNumber(), to::setFloorNumber, isSkipNull);
        if(c==null || c.fd("minSalary").checkEnable())
        setVal(from.getMinSalary(), to::setMinSalary, isSkipNull);
        if(c==null || c.fd("status").checkEnable())
        setVal(from.getStatus(), to::setStatus, isSkipNull);
        if(c==null || c.fd("namePrefix").checkEnable())
        setVal(from.getNamePrefix(), to::setNamePrefix, isSkipNull);
        if(c==null || c.fd("nameSuffix").checkEnable())
        setVal(from.getNameSuffix(), to::setNameSuffix, isSkipNull);
        if(c==null || c.fd("namePrefixCI").checkEnable())
        setVal(from.getNamePrefixCI(), to::setNamePrefixCI, isSkipNull);
        if(c==null || c.fd("nameSuffixCI").checkEnable())
        setVal(from.getNameSuffixCI(), to::setNameSuffixCI, isSkipNull);
        if(c==null || c.fd("nameExactCI").checkEnable())
        setVal(from.getNameExactCI(), to::setNameExactCI, isSkipNull);
        if(c==null || c.fd("active").checkEnable())
        setVal(from.getActive(), to::setActive, isSkipNull);
        if(c==null || c.fd("internalCode").checkEnable())
        setVal(from.getInternalCode(), to::setInternalCode, isSkipNull);
        if(c==null || c.fd("externalCode").checkEnable())
        setVal(from.getExternalCode(), to::setExternalCode, isSkipNull);
        if(c==null || c.fd("legacyCode").checkEnable())
        setVal(from.getLegacyCode(), to::setLegacyCode, isSkipNull);
        if(isSkip("Department","HrAuditEntity")) return;
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
        if(c==null || c.fd("version").checkEnable())
        setVal(from.getVersion(), to::setVersion, isSkipNull);
    }
    
    
    public static void mapping(Department from, DepartmentDTO to){
        mapping(from,to,false);
    }
    public static void mapping(Department from, DepartmentDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static DepartmentDTO toDTO(Department from){
        return toDTO(from,MappingContext.withGuard(2,null));
    }
    public static DepartmentDTO toDTO(Department from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        DepartmentDTO to = new DepartmentDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<DepartmentDTO> toDepartmentDTOList(List<Department> fromList){
        return toDepartmentDTOList(fromList,(MappingContext) null);
    }
    public static List<DepartmentDTO> toDepartmentDTOList(List<Department> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<DepartmentDTO> toDepartmentDTOList(List<Department> fromList, BiFunction<Department, DepartmentDTO, DepartmentDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                DepartmentDTO to = toDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(Skill from, SkillDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(Skill from, SkillDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("skillId").checkEnable())
        setVal(from.getSkillId(), to::setSkillId, isSkipNull);
        if(c==null || c.fd("category").checkEnable())
        setVal(from.getCategory(), to::setCategory, isSkipNull);
        if(isSkip("Skill","SkillBase")) return;
        if(c==null || c.fd("skillName").checkEnable())
        setVal(from.getSkillName(), to::setSkillName, isSkipNull);
        if(c==null || c.fd("proficiency").checkEnable())
        setVal(from.getProficiency(), to::setProficiency, isSkipNull);
    }
    
    
    public static void mapping(Skill from, SkillDTO to){
        mapping(from,to,false);
    }
    public static void mapping(Skill from, SkillDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static SkillDTO toDTO(Skill from){
        return toDTO(from,MappingContext.withGuard(2,null));
    }
    public static SkillDTO toDTO(Skill from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        SkillDTO to = new SkillDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<SkillDTO> toSkillDTOList(List<Skill> fromList){
        return toSkillDTOList(fromList,(MappingContext) null);
    }
    public static List<SkillDTO> toSkillDTOList(List<Skill> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<SkillDTO> toSkillDTOList(List<Skill> fromList, BiFunction<Skill, SkillDTO, SkillDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                SkillDTO to = toDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(Employee from, EmployeeDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(Employee from, EmployeeDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(hasValue(from.getDepartment()))
            to.setDeptName(from.getDepartment().getName());
        if(hasValue(from.getDepartment()))
            to.setDeptCode(from.getDepartment().getCode());
        if(hasValue(from.getManager()))
            to.setManagerName(from.getManager().getFirstName());
        if(c==null || c.fd("empId").checkEnable())
        setVal(from.getEmpId(), to::setEmpId, isSkipNull);
        if(c==null || c.fd("firstName").checkEnable())
        setVal(from.getFirstName(), to::setFirstName, isSkipNull);
        if(c==null || c.fd("lastName").checkEnable())
        setVal(from.getLastName(), to::setLastName, isSkipNull);
        if(c==null || c.fd("email").checkEnable())
        setVal(from.getEmail(), to::setEmail, isSkipNull);
        if(c==null || c.fd("phone").checkEnable())
        setVal(from.getPhone(), to::setPhone, isSkipNull);
        if(c==null || c.fd("hireDate").checkEnable())
        setVal(from.getHireDate(), to::setHireDate, isSkipNull);
        if(c==null || c.fd("salary").checkEnable())
        setVal(from.getSalary(), to::setSalary, isSkipNull);
        if(c==null || c.fd("bonusRate").checkEnable())
        setVal(from.getBonusRate(), to::setBonusRate, isSkipNull);
        if(c==null || c.fd("active").checkEnable())
        setVal(from.getActive(), to::setActive, isSkipNull);
        if(isSkip("Employee","HrAuditEntity")) return;
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
        if(c==null || c.fd("version").checkEnable())
        setVal(from.getVersion(), to::setVersion, isSkipNull);
    }
    
    
    public static void mapping(Employee from, EmployeeDTO to){
        mapping(from,to,false);
    }
    public static void mapping(Employee from, EmployeeDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static EmployeeDTO toDTO(Employee from){
        return toDTO(from,MappingContext.withGuard(2,null));
    }
    public static EmployeeDTO toDTO(Employee from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        EmployeeDTO to = new EmployeeDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<EmployeeDTO> toEmployeeDTOList(List<Employee> fromList){
        return toEmployeeDTOList(fromList,(MappingContext) null);
    }
    public static List<EmployeeDTO> toEmployeeDTOList(List<Employee> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<EmployeeDTO> toEmployeeDTOList(List<Employee> fromList, BiFunction<Employee, EmployeeDTO, EmployeeDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                EmployeeDTO to = toDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(Employee from, EmployeeContactDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(Employee from, EmployeeContactDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(hasValue(from.getDepartment()))
            to.setDeptName(from.getDepartment().getName());
        if(c==null || c.fd("firstName").checkEnable())
        setVal(from.getFirstName(), to::setFirstName, isSkipNull);
        if(c==null || c.fd("lastName").checkEnable())
        setVal(from.getLastName(), to::setLastName, isSkipNull);
        if(c==null || c.fd("email").checkEnable())
        setVal(from.getEmail(), to::setEmail, isSkipNull);
        if(c==null || c.fd("phone").checkEnable())
        setVal(from.getPhone(), to::setPhone, isSkipNull);
    }
    
    
    public static void mapping(Employee from, EmployeeContactDTO to){
        mapping(from,to,false);
    }
    public static void mapping(Employee from, EmployeeContactDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static EmployeeContactDTO toEmployeeContactDTO(Employee from){
        return toEmployeeContactDTO(from,MappingContext.withGuard(2,null));
    }
    public static EmployeeContactDTO toEmployeeContactDTO(Employee from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        EmployeeContactDTO to = new EmployeeContactDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<EmployeeContactDTO> toEmployeeContactDTOList(List<Employee> fromList){
        return toEmployeeContactDTOList(fromList,(MappingContext) null);
    }
    public static List<EmployeeContactDTO> toEmployeeContactDTOList(List<Employee> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEmployeeContactDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<EmployeeContactDTO> toEmployeeContactDTOList(List<Employee> fromList, BiFunction<Employee, EmployeeContactDTO, EmployeeContactDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                EmployeeContactDTO to = toEmployeeContactDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(ProjectAssignment from, ProjectAssignmentDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(ProjectAssignment from, ProjectAssignmentDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("empId").checkEnable())
        setVal(from.getEmpId(), to::setEmpId, isSkipNull);
        if(c==null || c.fd("projectCode").checkEnable())
        setVal(from.getProjectCode(), to::setProjectCode, isSkipNull);
        if(c==null || c.fd("role").checkEnable())
        setVal(from.getRole(), to::setRole, isSkipNull);
        if(c==null || c.fd("assignDate").checkEnable())
        setVal(from.getAssignDate(), to::setAssignDate, isSkipNull);
        if(c==null || c.fd("hoursAllocated").checkEnable())
        setVal(from.getHoursAllocated(), to::setHoursAllocated, isSkipNull);
    }
    
    
    public static void mapping(ProjectAssignment from, ProjectAssignmentDTO to){
        mapping(from,to,false);
    }
    public static void mapping(ProjectAssignment from, ProjectAssignmentDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static ProjectAssignmentDTO toDTO(ProjectAssignment from){
        return toDTO(from,MappingContext.withGuard(2,null));
    }
    public static ProjectAssignmentDTO toDTO(ProjectAssignment from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        ProjectAssignmentDTO to = new ProjectAssignmentDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<ProjectAssignmentDTO> toProjectAssignmentDTOList(List<ProjectAssignment> fromList){
        return toProjectAssignmentDTOList(fromList,(MappingContext) null);
    }
    public static List<ProjectAssignmentDTO> toProjectAssignmentDTOList(List<ProjectAssignment> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<ProjectAssignmentDTO> toProjectAssignmentDTOList(List<ProjectAssignment> fromList, BiFunction<ProjectAssignment, ProjectAssignmentDTO, ProjectAssignmentDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                ProjectAssignmentDTO to = toDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(Project from, ProjectDTO to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(Project from, ProjectDTO to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("projectCode").checkEnable())
        setVal(from.getProjectCode(), to::setProjectCode, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("startDate").checkEnable())
        setVal(from.getStartDate(), to::setStartDate, isSkipNull);
        if(c==null || c.fd("endDate").checkEnable())
        setVal(from.getEndDate(), to::setEndDate, isSkipNull);
        if(c==null || c.fd("budget").checkEnable())
        setVal(from.getBudget(), to::setBudget, isSkipNull);
        if(c==null || c.fd("status").checkEnable())
        setVal(from.getStatus(), to::setStatus, isSkipNull);
    }
    
    
    public static void mapping(Project from, ProjectDTO to){
        mapping(from,to,false);
    }
    public static void mapping(Project from, ProjectDTO to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static ProjectDTO toDTO(Project from){
        return toDTO(from,MappingContext.withGuard(2,null));
    }
    public static ProjectDTO toDTO(Project from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        ProjectDTO to = new ProjectDTO();
        mapping(from,to,c);
        return to;
    }
    
    
    public static List<ProjectDTO> toProjectDTOList(List<Project> fromList){
        return toProjectDTOList(fromList,(MappingContext) null);
    }
    public static List<ProjectDTO> toProjectDTOList(List<Project> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toDTO(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<ProjectDTO> toProjectDTOList(List<Project> fromList, BiFunction<Project, ProjectDTO, ProjectDTO> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                ProjectDTO to = toDTO(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(EmployeeDTO from, Employee to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(EmployeeDTO from, Employee to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("empId").checkEnable())
        setVal(from.getEmpId(), to::setEmpId, isSkipNull);
        if(c==null || c.fd("firstName").checkEnable())
        setVal(from.getFirstName(), to::setFirstName, isSkipNull);
        if(c==null || c.fd("lastName").checkEnable())
        setVal(from.getLastName(), to::setLastName, isSkipNull);
        if(c==null || c.fd("email").checkEnable())
        setVal(from.getEmail(), to::setEmail, isSkipNull);
        if(c==null || c.fd("phone").checkEnable())
        setVal(from.getPhone(), to::setPhone, isSkipNull);
        if(c==null || c.fd("hireDate").checkEnable())
        setVal(from.getHireDate(), to::setHireDate, isSkipNull);
        if(c==null || c.fd("salary").checkEnable())
        setVal(from.getSalary(), to::setSalary, isSkipNull);
        if(c==null || c.fd("bonusRate").checkEnable())
        setVal(from.getBonusRate(), to::setBonusRate, isSkipNull);
        if(c==null || c.fd("active").checkEnable())
        setVal(from.getActive(), to::setActive, isSkipNull);
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
        if(c==null || c.fd("version").checkEnable())
        setVal(from.getVersion(), to::setVersion, isSkipNull);
        if(hasValue(from.getDeptName()) && hasValue(to.getDepartment()))
            to.getDepartment().setName(from.getDeptName());
        if(hasValue(from.getDeptCode()) && hasValue(to.getDepartment()))
            to.getDepartment().setCode(from.getDeptCode());
        if(hasValue(from.getManagerName()) && hasValue(to.getManager()))
            to.getManager().setFirstName(from.getManagerName());
    }
    
    
    public static void mapping(EmployeeDTO from, Employee to){
        mapping(from,to,false);
    }
    public static void mapping(EmployeeDTO from, Employee to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(EmployeeDTO from, Employee to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(EmployeeDTO from, Employee to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(EmployeeDTO from, Employee to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(EmployeeDTO from, Employee to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("empId").checkEnable())
        setVal(from.getEmpId(), to::setEmpId, isSkipNull);
        if(c==null || c.fd("firstName").checkEnable())
        setVal(from.getFirstName(), to::setFirstName, isSkipNull);
        if(c==null || c.fd("lastName").checkEnable())
        setVal(from.getLastName(), to::setLastName, isSkipNull);
        if(c==null || c.fd("email").checkEnable())
        setVal(from.getEmail(), to::setEmail, isSkipNull);
        if(c==null || c.fd("phone").checkEnable())
        setVal(from.getPhone(), to::setPhone, isSkipNull);
        if(c==null || c.fd("hireDate").checkEnable())
        setVal(from.getHireDate(), to::setHireDate, isSkipNull);
        if(c==null || c.fd("salary").checkEnable())
        setVal(from.getSalary(), to::setSalary, isSkipNull);
        if(c==null || c.fd("bonusRate").checkEnable())
        setVal(from.getBonusRate(), to::setBonusRate, isSkipNull);
        if(c==null || c.fd("active").checkEnable())
        setVal(from.getActive(), to::setActive, isSkipNull);
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
        if(c==null || c.fd("version").checkEnable())
        setVal(from.getVersion(), to::setVersion, isSkipNull);
        if(hasValue(from.getDeptName()) && hasValue(to.getDepartment()))
            to.getDepartment().setName(from.getDeptName());
        if(hasValue(from.getDeptCode()) && hasValue(to.getDepartment()))
            to.getDepartment().setCode(from.getDeptCode());
        if(hasValue(from.getManagerName()) && hasValue(to.getManager()))
            to.getManager().setFirstName(from.getManagerName());
    }
    
    
    public static Employee toEntity(EmployeeDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static Employee toEntity(EmployeeDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Employee to = new Employee();
        mapping(from,to,c);
        return to;
    }
    
    
    public static Employee toEntity(EmployeeDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static Employee toEntity(EmployeeDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Employee to = new Employee();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<Employee> toEmployeeList(List<EmployeeDTO> fromList){
        return toEmployeeList(fromList,(MappingContext) null);
    }
    public static List<Employee> toEmployeeList(List<EmployeeDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<Employee> toEmployeeList(List<EmployeeDTO> fromList, EntityManager em){
        return toEmployeeList(fromList,(MappingContext) null,em);
    }
    public static List<Employee> toEmployeeList(List<EmployeeDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<Employee> toEmployeeList(List<EmployeeDTO> fromList, BiFunction<EmployeeDTO, Employee, Employee> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                Employee to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(EmployeeContactDTO from, Employee to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(EmployeeContactDTO from, Employee to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("firstName").checkEnable())
        setVal(from.getFirstName(), to::setFirstName, isSkipNull);
        if(c==null || c.fd("lastName").checkEnable())
        setVal(from.getLastName(), to::setLastName, isSkipNull);
        if(c==null || c.fd("email").checkEnable())
        setVal(from.getEmail(), to::setEmail, isSkipNull);
        if(c==null || c.fd("phone").checkEnable())
        setVal(from.getPhone(), to::setPhone, isSkipNull);
        if(hasValue(from.getDeptName()) && hasValue(to.getDepartment()))
            to.getDepartment().setName(from.getDeptName());
    }
    
    
    public static void mapping(EmployeeContactDTO from, Employee to){
        mapping(from,to,false);
    }
    public static void mapping(EmployeeContactDTO from, Employee to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(EmployeeContactDTO from, Employee to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(EmployeeContactDTO from, Employee to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(EmployeeContactDTO from, Employee to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(EmployeeContactDTO from, Employee to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("firstName").checkEnable())
        setVal(from.getFirstName(), to::setFirstName, isSkipNull);
        if(c==null || c.fd("lastName").checkEnable())
        setVal(from.getLastName(), to::setLastName, isSkipNull);
        if(c==null || c.fd("email").checkEnable())
        setVal(from.getEmail(), to::setEmail, isSkipNull);
        if(c==null || c.fd("phone").checkEnable())
        setVal(from.getPhone(), to::setPhone, isSkipNull);
        if(hasValue(from.getDeptName()) && hasValue(to.getDepartment()))
            to.getDepartment().setName(from.getDeptName());
    }
    
    
    public static Employee toEntity(EmployeeContactDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static Employee toEntity(EmployeeContactDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Employee to = new Employee();
        mapping(from,to,c);
        return to;
    }
    
    
    public static Employee toEntity(EmployeeContactDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static Employee toEntity(EmployeeContactDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Employee to = new Employee();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<Employee> toEmployee2List(List<EmployeeContactDTO> fromList){
        return toEmployee2List(fromList,(MappingContext) null);
    }
    public static List<Employee> toEmployee2List(List<EmployeeContactDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<Employee> toEmployee2List(List<EmployeeContactDTO> fromList, EntityManager em){
        return toEmployee2List(fromList,(MappingContext) null,em);
    }
    public static List<Employee> toEmployee2List(List<EmployeeContactDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<Employee> toEmployee2List(List<EmployeeContactDTO> fromList, BiFunction<EmployeeContactDTO, Employee, Employee> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                Employee to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(DepartmentDTO from, Department to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(DepartmentDTO from, Department to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("deptId").checkEnable())
        setVal(from.getDeptId(), to::setDeptId, isSkipNull);
        if(c==null || c.fd("code").checkEnable())
        setVal(from.getCode(), to::setCode, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("nameInsensitive").checkEnable())
        setVal(from.getNameInsensitive(), to::setNameInsensitive, isSkipNull);
        if(c==null || c.fd("description").checkEnable())
        setVal(from.getDescription(), to::setDescription, isSkipNull);
        if(c==null || c.fd("budget").checkEnable())
        setVal(from.getBudget(), to::setBudget, isSkipNull);
        if(c==null || c.fd("headCount").checkEnable())
        setVal(from.getHeadCount(), to::setHeadCount, isSkipNull);
        if(c==null || c.fd("maxCapacity").checkEnable())
        setVal(from.getMaxCapacity(), to::setMaxCapacity, isSkipNull);
        if(c==null || c.fd("floorNumber").checkEnable())
        setVal(from.getFloorNumber(), to::setFloorNumber, isSkipNull);
        if(c==null || c.fd("minSalary").checkEnable())
        setVal(from.getMinSalary(), to::setMinSalary, isSkipNull);
        if(c==null || c.fd("status").checkEnable())
        setVal(from.getStatus(), to::setStatus, isSkipNull);
        if(c==null || c.fd("namePrefix").checkEnable())
        setVal(from.getNamePrefix(), to::setNamePrefix, isSkipNull);
        if(c==null || c.fd("nameSuffix").checkEnable())
        setVal(from.getNameSuffix(), to::setNameSuffix, isSkipNull);
        if(c==null || c.fd("namePrefixCI").checkEnable())
        setVal(from.getNamePrefixCI(), to::setNamePrefixCI, isSkipNull);
        if(c==null || c.fd("nameSuffixCI").checkEnable())
        setVal(from.getNameSuffixCI(), to::setNameSuffixCI, isSkipNull);
        if(c==null || c.fd("nameExactCI").checkEnable())
        setVal(from.getNameExactCI(), to::setNameExactCI, isSkipNull);
        if(c==null || c.fd("active").checkEnable())
        setVal(from.getActive(), to::setActive, isSkipNull);
        if(c==null || c.fd("internalCode").checkEnable())
        setVal(from.getInternalCode(), to::setInternalCode, isSkipNull);
        if(c==null || c.fd("externalCode").checkEnable())
        setVal(from.getExternalCode(), to::setExternalCode, isSkipNull);
        if(c==null || c.fd("legacyCode").checkEnable())
        setVal(from.getLegacyCode(), to::setLegacyCode, isSkipNull);
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
        if(c==null || c.fd("version").checkEnable())
        setVal(from.getVersion(), to::setVersion, isSkipNull);
    }
    
    
    public static void mapping(DepartmentDTO from, Department to){
        mapping(from,to,false);
    }
    public static void mapping(DepartmentDTO from, Department to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(DepartmentDTO from, Department to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(DepartmentDTO from, Department to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(DepartmentDTO from, Department to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(DepartmentDTO from, Department to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("deptId").checkEnable())
        setVal(from.getDeptId(), to::setDeptId, isSkipNull);
        if(c==null || c.fd("code").checkEnable())
        setVal(from.getCode(), to::setCode, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("nameInsensitive").checkEnable())
        setVal(from.getNameInsensitive(), to::setNameInsensitive, isSkipNull);
        if(c==null || c.fd("description").checkEnable())
        setVal(from.getDescription(), to::setDescription, isSkipNull);
        if(c==null || c.fd("budget").checkEnable())
        setVal(from.getBudget(), to::setBudget, isSkipNull);
        if(c==null || c.fd("headCount").checkEnable())
        setVal(from.getHeadCount(), to::setHeadCount, isSkipNull);
        if(c==null || c.fd("maxCapacity").checkEnable())
        setVal(from.getMaxCapacity(), to::setMaxCapacity, isSkipNull);
        if(c==null || c.fd("floorNumber").checkEnable())
        setVal(from.getFloorNumber(), to::setFloorNumber, isSkipNull);
        if(c==null || c.fd("minSalary").checkEnable())
        setVal(from.getMinSalary(), to::setMinSalary, isSkipNull);
        if(c==null || c.fd("status").checkEnable())
        setVal(from.getStatus(), to::setStatus, isSkipNull);
        if(c==null || c.fd("namePrefix").checkEnable())
        setVal(from.getNamePrefix(), to::setNamePrefix, isSkipNull);
        if(c==null || c.fd("nameSuffix").checkEnable())
        setVal(from.getNameSuffix(), to::setNameSuffix, isSkipNull);
        if(c==null || c.fd("namePrefixCI").checkEnable())
        setVal(from.getNamePrefixCI(), to::setNamePrefixCI, isSkipNull);
        if(c==null || c.fd("nameSuffixCI").checkEnable())
        setVal(from.getNameSuffixCI(), to::setNameSuffixCI, isSkipNull);
        if(c==null || c.fd("nameExactCI").checkEnable())
        setVal(from.getNameExactCI(), to::setNameExactCI, isSkipNull);
        if(c==null || c.fd("active").checkEnable())
        setVal(from.getActive(), to::setActive, isSkipNull);
        if(c==null || c.fd("internalCode").checkEnable())
        setVal(from.getInternalCode(), to::setInternalCode, isSkipNull);
        if(c==null || c.fd("externalCode").checkEnable())
        setVal(from.getExternalCode(), to::setExternalCode, isSkipNull);
        if(c==null || c.fd("legacyCode").checkEnable())
        setVal(from.getLegacyCode(), to::setLegacyCode, isSkipNull);
        if(c==null || c.fd("createdBy").checkEnable())
        setVal(from.getCreatedBy(), to::setCreatedBy, isSkipNull);
        if(c==null || c.fd("createDate").checkEnable())
        setVal(from.getCreateDate(), to::setCreateDate, isSkipNull);
        if(c==null || c.fd("lastModifiedBy").checkEnable())
        setVal(from.getLastModifiedBy(), to::setLastModifiedBy, isSkipNull);
        if(c==null || c.fd("lastModifiedDate").checkEnable())
        setVal(from.getLastModifiedDate(), to::setLastModifiedDate, isSkipNull);
        if(c==null || c.fd("version").checkEnable())
        setVal(from.getVersion(), to::setVersion, isSkipNull);
    }
    
    
    public static Department toEntity(DepartmentDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static Department toEntity(DepartmentDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Department to = new Department();
        mapping(from,to,c);
        return to;
    }
    
    
    public static Department toEntity(DepartmentDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static Department toEntity(DepartmentDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Department to = new Department();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<Department> toDepartmentList(List<DepartmentDTO> fromList){
        return toDepartmentList(fromList,(MappingContext) null);
    }
    public static List<Department> toDepartmentList(List<DepartmentDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<Department> toDepartmentList(List<DepartmentDTO> fromList, EntityManager em){
        return toDepartmentList(fromList,(MappingContext) null,em);
    }
    public static List<Department> toDepartmentList(List<DepartmentDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<Department> toDepartmentList(List<DepartmentDTO> fromList, BiFunction<DepartmentDTO, Department, Department> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                Department to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(ProjectDTO from, Project to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(ProjectDTO from, Project to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("projectCode").checkEnable())
        setVal(from.getProjectCode(), to::setProjectCode, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("startDate").checkEnable())
        setVal(from.getStartDate(), to::setStartDate, isSkipNull);
        if(c==null || c.fd("endDate").checkEnable())
        setVal(from.getEndDate(), to::setEndDate, isSkipNull);
        if(c==null || c.fd("budget").checkEnable())
        setVal(from.getBudget(), to::setBudget, isSkipNull);
        if(c==null || c.fd("status").checkEnable())
        setVal(from.getStatus(), to::setStatus, isSkipNull);
    }
    
    
    public static void mapping(ProjectDTO from, Project to){
        mapping(from,to,false);
    }
    public static void mapping(ProjectDTO from, Project to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(ProjectDTO from, Project to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(ProjectDTO from, Project to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(ProjectDTO from, Project to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(ProjectDTO from, Project to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("projectCode").checkEnable())
        setVal(from.getProjectCode(), to::setProjectCode, isSkipNull);
        if(c==null || c.fd("name").checkEnable())
        setVal(from.getName(), to::setName, isSkipNull);
        if(c==null || c.fd("startDate").checkEnable())
        setVal(from.getStartDate(), to::setStartDate, isSkipNull);
        if(c==null || c.fd("endDate").checkEnable())
        setVal(from.getEndDate(), to::setEndDate, isSkipNull);
        if(c==null || c.fd("budget").checkEnable())
        setVal(from.getBudget(), to::setBudget, isSkipNull);
        if(c==null || c.fd("status").checkEnable())
        setVal(from.getStatus(), to::setStatus, isSkipNull);
    }
    
    
    public static Project toEntity(ProjectDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static Project toEntity(ProjectDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Project to = new Project();
        mapping(from,to,c);
        return to;
    }
    
    
    public static Project toEntity(ProjectDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static Project toEntity(ProjectDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Project to = new Project();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<Project> toProjectList(List<ProjectDTO> fromList){
        return toProjectList(fromList,(MappingContext) null);
    }
    public static List<Project> toProjectList(List<ProjectDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<Project> toProjectList(List<ProjectDTO> fromList, EntityManager em){
        return toProjectList(fromList,(MappingContext) null,em);
    }
    public static List<Project> toProjectList(List<ProjectDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<Project> toProjectList(List<ProjectDTO> fromList, BiFunction<ProjectDTO, Project, Project> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                Project to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(SkillDTO from, Skill to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(SkillDTO from, Skill to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("skillId").checkEnable())
        setVal(from.getSkillId(), to::setSkillId, isSkipNull);
        if(c==null || c.fd("category").checkEnable())
        setVal(from.getCategory(), to::setCategory, isSkipNull);
        if(c==null || c.fd("skillName").checkEnable())
        setVal(from.getSkillName(), to::setSkillName, isSkipNull);
        if(c==null || c.fd("proficiency").checkEnable())
        setVal(from.getProficiency(), to::setProficiency, isSkipNull);
    }
    
    
    public static void mapping(SkillDTO from, Skill to){
        mapping(from,to,false);
    }
    public static void mapping(SkillDTO from, Skill to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(SkillDTO from, Skill to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(SkillDTO from, Skill to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(SkillDTO from, Skill to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(SkillDTO from, Skill to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("skillId").checkEnable())
        setVal(from.getSkillId(), to::setSkillId, isSkipNull);
        if(c==null || c.fd("category").checkEnable())
        setVal(from.getCategory(), to::setCategory, isSkipNull);
        if(c==null || c.fd("skillName").checkEnable())
        setVal(from.getSkillName(), to::setSkillName, isSkipNull);
        if(c==null || c.fd("proficiency").checkEnable())
        setVal(from.getProficiency(), to::setProficiency, isSkipNull);
    }
    
    
    public static Skill toEntity(SkillDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static Skill toEntity(SkillDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Skill to = new Skill();
        mapping(from,to,c);
        return to;
    }
    
    
    public static Skill toEntity(SkillDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static Skill toEntity(SkillDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        Skill to = new Skill();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<Skill> toSkillList(List<SkillDTO> fromList){
        return toSkillList(fromList,(MappingContext) null);
    }
    public static List<Skill> toSkillList(List<SkillDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<Skill> toSkillList(List<SkillDTO> fromList, EntityManager em){
        return toSkillList(fromList,(MappingContext) null,em);
    }
    public static List<Skill> toSkillList(List<SkillDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<Skill> toSkillList(List<SkillDTO> fromList, BiFunction<SkillDTO, Skill, Skill> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                Skill to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static void mapping(ProjectAssignmentDTO from, ProjectAssignment to, boolean isSkipNull){
        mapping(from,to,isSkipNull,(MappingContext) null);
    }
    public static void mapping(ProjectAssignmentDTO from, ProjectAssignment to, boolean isSkipNull, MappingContext c){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("empId").checkEnable())
        setVal(from.getEmpId(), to::setEmpId, isSkipNull);
        if(c==null || c.fd("projectCode").checkEnable())
        setVal(from.getProjectCode(), to::setProjectCode, isSkipNull);
        if(c==null || c.fd("role").checkEnable())
        setVal(from.getRole(), to::setRole, isSkipNull);
        if(c==null || c.fd("assignDate").checkEnable())
        setVal(from.getAssignDate(), to::setAssignDate, isSkipNull);
        if(c==null || c.fd("hoursAllocated").checkEnable())
        setVal(from.getHoursAllocated(), to::setHoursAllocated, isSkipNull);
    }
    
    
    public static void mapping(ProjectAssignmentDTO from, ProjectAssignment to){
        mapping(from,to,false);
    }
    public static void mapping(ProjectAssignmentDTO from, ProjectAssignment to, MappingContext c){
        mapping(from,to,false,c);
    }
    
    
    public static void mapping(ProjectAssignmentDTO from, ProjectAssignment to, EntityManager em){
        mapping(from,to,false,null,em);
    }
    public static void mapping(ProjectAssignmentDTO from, ProjectAssignment to, boolean isSkipNull, EntityManager em){
        mapping(from,to,isSkipNull,null,em);
    }
    public static void mapping(ProjectAssignmentDTO from, ProjectAssignment to, MappingContext c, EntityManager em){
        mapping(from,to,false,c,em);
    }
    public static void mapping(ProjectAssignmentDTO from, ProjectAssignment to, boolean isSkipNull, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable()) return;
        checkPermission(from, to);
        if(from ==null || to ==null) return;
        if(c==null || c.fd("empId").checkEnable())
        setVal(from.getEmpId(), to::setEmpId, isSkipNull);
        if(c==null || c.fd("projectCode").checkEnable())
        setVal(from.getProjectCode(), to::setProjectCode, isSkipNull);
        if(c==null || c.fd("role").checkEnable())
        setVal(from.getRole(), to::setRole, isSkipNull);
        if(c==null || c.fd("assignDate").checkEnable())
        setVal(from.getAssignDate(), to::setAssignDate, isSkipNull);
        if(c==null || c.fd("hoursAllocated").checkEnable())
        setVal(from.getHoursAllocated(), to::setHoursAllocated, isSkipNull);
    }
    
    
    public static ProjectAssignment toEntity(ProjectAssignmentDTO from){
        return toEntity(from,MappingContext.withGuard(2,null));
    }
    public static ProjectAssignment toEntity(ProjectAssignmentDTO from,MappingContext c1){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        ProjectAssignment to = new ProjectAssignment();
        mapping(from,to,c);
        return to;
    }
    
    
    public static ProjectAssignment toEntity(ProjectAssignmentDTO from, EntityManager em){
        return toEntity(from,MappingContext.withGuard(2,null),em);
    }
    public static ProjectAssignment toEntity(ProjectAssignmentDTO from, MappingContext c1, EntityManager em){
        MappingContext c=c1!=null?c1.getChild():null;
        if(c!=null && !c.checkEnable()) return null;
        if(from==null) return null;
        ProjectAssignment to = new ProjectAssignment();
        mapping(from,to,c,em);
        return to;
    }
    
    
    public static List<ProjectAssignment> toProjectAssignmentList(List<ProjectAssignmentDTO> fromList){
        return toProjectAssignmentList(fromList,(MappingContext) null);
    }
    public static List<ProjectAssignment> toProjectAssignmentList(List<ProjectAssignmentDTO> fromList, MappingContext c){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c)).collect(Collectors.toList());
    }
    
    
    public static List<ProjectAssignment> toProjectAssignmentList(List<ProjectAssignmentDTO> fromList, EntityManager em){
        return toProjectAssignmentList(fromList,(MappingContext) null,em);
    }
    public static List<ProjectAssignment> toProjectAssignmentList(List<ProjectAssignmentDTO> fromList, MappingContext c, EntityManager em){
        if(c!=null && !c.checkEnable(1)) return null;
        if(fromList==null) return null;
        return fromList.stream().map(e->HrMapper.toEntity(e,c,em)).collect(Collectors.toList());
    }
    
    
    public static List<ProjectAssignment> toProjectAssignmentList(List<ProjectAssignmentDTO> fromList, BiFunction<ProjectAssignmentDTO, ProjectAssignment, ProjectAssignment> modifier){
        if(fromList==null) return null;
        return fromList.stream()
            .map(from -> {
                ProjectAssignment to = toEntity(from);
                return modifier.apply(from, to);
            }
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
    }
    
    
    public static Specification<Employee> toSpec(EmployeeSrchDTO searchDTO){
        Specification<Employee> b = (r, q, c) -> c.conjunction();
        return toSpec(searchDTO, b);
    }
    
    
    public static Specification<Employee> toSpec(EmployeeSrchDTO searchDTO, Specification<Employee> sp){
        String deptCode = searchDTO.getDeptCode();
        if(hasValue(deptCode)){
            sp = sp.and((r,q,c) -> c.equal(r.get("department").get("code"),deptCode));
        }
        String deptName = searchDTO.getDeptName();
        if(hasValue(deptName)){
            sp = sp.and((r,q,c) -> c.like(r.get("department").get("name"),"%" +trim(deptName)+ "%"));
        }
        String firstName = searchDTO.getFirstName();
        if(hasValue(firstName)){
            sp = sp.and((r,q,c) -> c.like(r.get("firstName"),"%" +trim(firstName)+ "%"));
        }
        String lastName = searchDTO.getLastName();
        if(hasValue(lastName)){
            sp = sp.and((r,q,c) -> c.like(r.get("lastName"),"%" +trim(lastName)+ "%"));
        }
        String email = searchDTO.getEmail();
        if(hasValue(email)){
            sp = sp.and((r,q,c) -> c.equal(r.get("email"),email));
        }
        String phone = searchDTO.getPhone();
        if(hasValue(phone)){
            sp = sp.and((r,q,c) -> c.like(c.lower(r.get("phone")),"%" +phone.toLowerCase()+ "%"));
        }
        java.time.LocalDate hireDateFrom = searchDTO.getHireDateFrom();
        java.time.LocalDate hireDateTo = searchDTO.getHireDateTo();
        if(hasValue(hireDateFrom) && hasValue(hireDateTo)){
            java.time.LocalDate hireDateTo_2 = hireDateTo.plusDays(1);
            sp = sp.and((r,q,c) -> c.between(r.get("hireDate"),hireDateFrom,hireDateTo_2));
        }
        else if(hasValue(hireDateFrom)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("hireDate"),hireDateFrom));
        }
        else if(hasValue(hireDateTo)){
            java.time.LocalDate hireDateTo_2 = hireDateTo.plusDays(1);
            sp = sp.and((r,q,c) -> c.lessThan(r.get("hireDate"),hireDateTo_2));
        }
        java.math.BigDecimal salaryFrom = searchDTO.getSalaryFrom();
        java.math.BigDecimal salaryTo = searchDTO.getSalaryTo();
        if(hasValue(salaryFrom) && hasValue(salaryTo)){
            sp = sp.and((r,q,c) -> c.between(r.get("salary"),salaryFrom,salaryTo));
        }
        else if(hasValue(salaryFrom)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("salary"),salaryFrom));
        }
        else if(hasValue(salaryTo)){
            sp = sp.and((r,q,c) -> c.lessThanOrEqualTo(r.get("salary"),salaryTo));
        }
        Boolean active = searchDTO.getActive();
        if(hasValue(active)){
            sp = sp.and((r,q,c) -> c.equal(r.get("active"),active));
        }
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
        return sp;
    }
    
    
    public static Specification<Department> toSpec(DepartmentSrchDTO searchDTO){
        Specification<Department> b = (r, q, c) -> c.conjunction();
        return toSpec(searchDTO, b);
    }
    
    
    public static Specification<Department> toSpec(DepartmentSrchDTO searchDTO, Specification<Department> sp){
        DeptStatus status = searchDTO.getStatus();
        if(hasValue(status)){
            sp = sp.and((r, q, c) -> r.get("status").in(status));
        }
        Long deptId = searchDTO.getDeptId();
        if(hasValue(deptId)){
            sp = sp.and((r,q,c) -> c.equal(r.get("deptId"),deptId));
        }
        String code = searchDTO.getCode();
        if(hasValue(code)){
            sp = sp.and((r,q,c) -> c.equal(r.get("code"),code));
        }
        String name = searchDTO.getName();
        if(hasValue(name)){
            sp = sp.and((r,q,c) -> c.like(r.get("name"),"%" +trim(name)+ "%"));
        }
        String nameInsensitive = searchDTO.getNameInsensitive();
        if(hasValue(nameInsensitive)){
            sp = sp.and((r,q,c) -> c.like(c.lower(r.get("nameInsensitive")),"%" +nameInsensitive.toLowerCase()+ "%"));
        }
        sp=setSpec(sp, "description", searchDTO.getDescription());
        java.math.BigDecimal budgetFrom = searchDTO.getBudgetFrom();
        java.math.BigDecimal budgetTo = searchDTO.getBudgetTo();
        if(hasValue(budgetFrom) && hasValue(budgetTo)){
            sp = sp.and((r,q,c) -> c.between(r.get("budget"),budgetFrom,budgetTo));
        }
        else if(hasValue(budgetFrom)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("budget"),budgetFrom));
        }
        else if(hasValue(budgetTo)){
            sp = sp.and((r,q,c) -> c.lessThanOrEqualTo(r.get("budget"),budgetTo));
        }
        Integer headCount = searchDTO.getHeadCount();
        if(hasValue(headCount)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("headCount"),headCount));
        }
        Integer maxCapacity = searchDTO.getMaxCapacity();
        if(hasValue(maxCapacity)){
            sp = sp.and((r,q,c) -> c.lessThanOrEqualTo(r.get("maxCapacity"),maxCapacity));
        }
        Integer floorNumber = searchDTO.getFloorNumber();
        if(hasValue(floorNumber)){
            sp = sp.and((r,q,c) -> c.greaterThan(r.get("floorNumber"),floorNumber));
        }
        java.math.BigDecimal minSalary = searchDTO.getMinSalary();
        if(hasValue(minSalary)){
            sp = sp.and((r,q,c) -> c.lessThan(r.get("minSalary"),minSalary));
        }
        String namePrefix = searchDTO.getNamePrefix();
        if(hasValue(namePrefix)){
            sp = sp.and((r,q,c) -> c.like(r.get("namePrefix"),trim(namePrefix)+ "%"));
        }
        String nameSuffix = searchDTO.getNameSuffix();
        if(hasValue(nameSuffix)){
            sp = sp.and((r,q,c) -> c.like(r.get("nameSuffix"),"%" +trim(nameSuffix)));
        }
        String namePrefixCI = searchDTO.getNamePrefixCI();
        if(hasValue(namePrefixCI)){
            sp = sp.and((r,q,c) -> c.like(c.lower(r.get("namePrefixCI")),namePrefixCI.toLowerCase()+ "%"));
        }
        String nameSuffixCI = searchDTO.getNameSuffixCI();
        if(hasValue(nameSuffixCI)){
            sp = sp.and((r,q,c) -> c.like(c.lower(r.get("nameSuffixCI")),"%" +nameSuffixCI.toLowerCase()));
        }
        String nameExactCI = searchDTO.getNameExactCI();
        if(hasValue(nameExactCI)){
            sp = sp.and((r,q,c) -> c.equal(c.lower(r.get("nameExactCI")),nameExactCI.toLowerCase()));
        }
        Boolean active = searchDTO.getActive();
        if(hasValue(active)){
            sp = sp.and((r,q,c) -> c.equal(r.get("active"),active));
        }
        sp=setSpec(sp, "internalCode", searchDTO.getInternalCode());
        sp=setSpec(sp, "externalCode", searchDTO.getExternalCode());
        sp=setSpec(sp, "legacyCode", searchDTO.getLegacyCode());
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
        Long version = searchDTO.getVersion();
        if(hasValue(version)){
            sp = sp.and((r,q,c) -> c.equal(r.get("version"),version));
        }
        return sp;
    }
    
    
    public static Specification<Skill> toSpec(SkillSrchDTO searchDTO){
        Specification<Skill> b = (r, q, c) -> c.conjunction();
        return toSpec(searchDTO, b);
    }
    
    
    public static Specification<Skill> toSpec(SkillSrchDTO searchDTO, Specification<Skill> sp){
        Long skillId = searchDTO.getSkillId();
        if(hasValue(skillId)){
            sp = sp.and((r,q,c) -> c.equal(r.get("skillId"),skillId));
        }
        String category = searchDTO.getCategory();
        if(hasValue(category)){
            sp = sp.and((r,q,c) -> c.equal(r.get("category"),category));
        }
        sp=setSpec(sp, "skillName", searchDTO.getSkillName());
        Integer proficiency = searchDTO.getProficiency();
        if(hasValue(proficiency)){
            sp = sp.and((r,q,c) -> c.equal(r.get("proficiency"),proficiency));
        }
        return sp;
    }
    
    
    public static Specification<ProjectAssignment> toSpec(ProjectAssignmentSrchDTO searchDTO){
        Specification<ProjectAssignment> b = (r, q, c) -> c.conjunction();
        return toSpec(searchDTO, b);
    }
    
    
    public static Specification<ProjectAssignment> toSpec(ProjectAssignmentSrchDTO searchDTO, Specification<ProjectAssignment> sp){
        return sp;
    }
    
    
    public static Specification<Project> toSpec(ProjectSrchDTO searchDTO){
        Specification<Project> b = (r, q, c) -> c.conjunction();
        return toSpec(searchDTO, b);
    }
    
    
    public static Specification<Project> toSpec(ProjectSrchDTO searchDTO, Specification<Project> sp){
        ProjectStatus status = searchDTO.getStatus();
        if(hasValue(status)){
            sp = sp.and((r, q, c) -> r.get("status").in(status));
        }
        sp=setSpec(sp, "projectCode", searchDTO.getProjectCode());
        String name = searchDTO.getName();
        if(hasValue(name)){
            sp = sp.and((r,q,c) -> c.like(r.get("name"),"%" +trim(name)+ "%"));
        }
        java.time.LocalDate startDateFrom = searchDTO.getStartDateFrom();
        java.time.LocalDate startDateTo = searchDTO.getStartDateTo();
        if(hasValue(startDateFrom) && hasValue(startDateTo)){
            java.time.LocalDate startDateTo_2 = startDateTo.plusDays(1);
            sp = sp.and((r,q,c) -> c.between(r.get("startDate"),startDateFrom,startDateTo_2));
        }
        else if(hasValue(startDateFrom)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("startDate"),startDateFrom));
        }
        else if(hasValue(startDateTo)){
            java.time.LocalDate startDateTo_2 = startDateTo.plusDays(1);
            sp = sp.and((r,q,c) -> c.lessThan(r.get("startDate"),startDateTo_2));
        }
        java.time.LocalDate endDateFrom = searchDTO.getEndDateFrom();
        java.time.LocalDate endDateTo = searchDTO.getEndDateTo();
        if(hasValue(endDateFrom) && hasValue(endDateTo)){
            java.time.LocalDate endDateTo_2 = endDateTo.plusDays(1);
            sp = sp.and((r,q,c) -> c.between(r.get("endDate"),endDateFrom,endDateTo_2));
        }
        else if(hasValue(endDateFrom)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("endDate"),endDateFrom));
        }
        else if(hasValue(endDateTo)){
            java.time.LocalDate endDateTo_2 = endDateTo.plusDays(1);
            sp = sp.and((r,q,c) -> c.lessThan(r.get("endDate"),endDateTo_2));
        }
        java.math.BigDecimal budgetFrom = searchDTO.getBudgetFrom();
        java.math.BigDecimal budgetTo = searchDTO.getBudgetTo();
        if(hasValue(budgetFrom) && hasValue(budgetTo)){
            sp = sp.and((r,q,c) -> c.between(r.get("budget"),budgetFrom,budgetTo));
        }
        else if(hasValue(budgetFrom)){
            sp = sp.and((r,q,c) -> c.greaterThanOrEqualTo(r.get("budget"),budgetFrom));
        }
        else if(hasValue(budgetTo)){
            sp = sp.and((r,q,c) -> c.lessThanOrEqualTo(r.get("budget"),budgetTo));
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
