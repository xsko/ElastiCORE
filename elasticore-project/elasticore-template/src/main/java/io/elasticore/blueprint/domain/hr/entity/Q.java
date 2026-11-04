//ecd:141770877H20261104091202_V3.0
package io.elasticore.blueprint.domain.hr.entity;


import io.elasticore.springboot3.entity.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
import io.elasticore.blueprint.domain.hr.enums.ProjectStatus;
import io.elasticore.blueprint.domain.hr.enums.DeptStatus;


/**
 * This class serves as a domain-specific unified metadata holder,
 * containing field names and type information for entities.
 *
 * Generated and managed by ElastiCORE.
 */

public class Q {

	public static $Department<Department> Department=new $Department<>();
	public static class $Department<T> {
	
	    private FieldInfo p=null;
	    public $Department() {}
	    public $Department(FieldInfo p) {this.p=p;}
	    public static JoinSpec<Department> joinSpec() {
	    	return new JoinSpec<>(Department.class);
	    }
	    public static UpdateSpec<Department> updateSpec() {
	    	return new UpdateSpec<>(Department.class);
	    }
	    public static DeleteSpec<Department> deleteSpec() {
	    	return new DeleteSpec<>(Department.class);
	    }
	
	    public final FieldInfo<T> getDeptId() {return new FieldInfo(Department.class,"deptId",Long.class, null,p);}
	    public final String deptId="deptId";
	    public Specification<T> deptId(Op op,Object value) {return getDeptId().where(op,value);}
	    public Specification<T> deptId(Op op,Object value,boolean chkEmpty) {return getDeptId().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getCode() {return new FieldInfo(Department.class,"code",String.class, null,p);}
	    public final String code="code";
	    public Specification<T> code(Op op,Object value) {return getCode().where(op,value);}
	    public Specification<T> code(Op op,Object value,boolean chkEmpty) {return getCode().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getName() {return new FieldInfo(Department.class,"name",String.class, null,p);}
	    public final String name="name";
	    public Specification<T> name(Op op,Object value) {return getName().where(op,value);}
	    public Specification<T> name(Op op,Object value,boolean chkEmpty) {return getName().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getNameInsensitive() {return new FieldInfo(Department.class,"nameInsensitive",String.class, null,p);}
	    public final String nameInsensitive="nameInsensitive";
	    public Specification<T> nameInsensitive(Op op,Object value) {return getNameInsensitive().where(op,value);}
	    public Specification<T> nameInsensitive(Op op,Object value,boolean chkEmpty) {return getNameInsensitive().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getDescription() {return new FieldInfo(Department.class,"description",String.class, null,p);}
	    public final String description="description";
	    public Specification<T> description(Op op,Object value) {return getDescription().where(op,value);}
	    public Specification<T> description(Op op,Object value,boolean chkEmpty) {return getDescription().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getBudget() {return new FieldInfo(Department.class,"budget",java.math.BigDecimal.class, null,p);}
	    public final String budget="budget";
	    public Specification<T> budget(Op op,Object value) {return getBudget().where(op,value);}
	    public Specification<T> budget(Op op,Object value,boolean chkEmpty) {return getBudget().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getHeadCount() {return new FieldInfo(Department.class,"headCount",Integer.class, null,p);}
	    public final String headCount="headCount";
	    public Specification<T> headCount(Op op,Object value) {return getHeadCount().where(op,value);}
	    public Specification<T> headCount(Op op,Object value,boolean chkEmpty) {return getHeadCount().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getMaxCapacity() {return new FieldInfo(Department.class,"maxCapacity",Integer.class, null,p);}
	    public final String maxCapacity="maxCapacity";
	    public Specification<T> maxCapacity(Op op,Object value) {return getMaxCapacity().where(op,value);}
	    public Specification<T> maxCapacity(Op op,Object value,boolean chkEmpty) {return getMaxCapacity().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getFloorNumber() {return new FieldInfo(Department.class,"floorNumber",Integer.class, null,p);}
	    public final String floorNumber="floorNumber";
	    public Specification<T> floorNumber(Op op,Object value) {return getFloorNumber().where(op,value);}
	    public Specification<T> floorNumber(Op op,Object value,boolean chkEmpty) {return getFloorNumber().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getMinSalary() {return new FieldInfo(Department.class,"minSalary",java.math.BigDecimal.class, null,p);}
	    public final String minSalary="minSalary";
	    public Specification<T> minSalary(Op op,Object value) {return getMinSalary().where(op,value);}
	    public Specification<T> minSalary(Op op,Object value,boolean chkEmpty) {return getMinSalary().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getStatus() {return new FieldInfo(Department.class,"status",DeptStatus.class, null,p);}
	    public final String status="status";
	    public Specification<T> status(Op op,Object value) {return getStatus().where(op,value);}
	    public Specification<T> status(Op op,Object value,boolean chkEmpty) {return getStatus().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getNamePrefix() {return new FieldInfo(Department.class,"namePrefix",String.class, null,p);}
	    public final String namePrefix="namePrefix";
	    public Specification<T> namePrefix(Op op,Object value) {return getNamePrefix().where(op,value);}
	    public Specification<T> namePrefix(Op op,Object value,boolean chkEmpty) {return getNamePrefix().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getNameSuffix() {return new FieldInfo(Department.class,"nameSuffix",String.class, null,p);}
	    public final String nameSuffix="nameSuffix";
	    public Specification<T> nameSuffix(Op op,Object value) {return getNameSuffix().where(op,value);}
	    public Specification<T> nameSuffix(Op op,Object value,boolean chkEmpty) {return getNameSuffix().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getNamePrefixCI() {return new FieldInfo(Department.class,"namePrefixCI",String.class, null,p);}
	    public final String namePrefixCI="namePrefixCI";
	    public Specification<T> namePrefixCI(Op op,Object value) {return getNamePrefixCI().where(op,value);}
	    public Specification<T> namePrefixCI(Op op,Object value,boolean chkEmpty) {return getNamePrefixCI().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getNameSuffixCI() {return new FieldInfo(Department.class,"nameSuffixCI",String.class, null,p);}
	    public final String nameSuffixCI="nameSuffixCI";
	    public Specification<T> nameSuffixCI(Op op,Object value) {return getNameSuffixCI().where(op,value);}
	    public Specification<T> nameSuffixCI(Op op,Object value,boolean chkEmpty) {return getNameSuffixCI().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getNameExactCI() {return new FieldInfo(Department.class,"nameExactCI",String.class, null,p);}
	    public final String nameExactCI="nameExactCI";
	    public Specification<T> nameExactCI(Op op,Object value) {return getNameExactCI().where(op,value);}
	    public Specification<T> nameExactCI(Op op,Object value,boolean chkEmpty) {return getNameExactCI().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getActive() {return new FieldInfo(Department.class,"active",Boolean.class, null,p);}
	    public final String active="active";
	    public Specification<T> active(Op op,Object value) {return getActive().where(op,value);}
	    public Specification<T> active(Op op,Object value,boolean chkEmpty) {return getActive().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getInternalCode() {return new FieldInfo(Department.class,"internalCode",String.class, null,p);}
	    public final String internalCode="internalCode";
	    public Specification<T> internalCode(Op op,Object value) {return getInternalCode().where(op,value);}
	    public Specification<T> internalCode(Op op,Object value,boolean chkEmpty) {return getInternalCode().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getExternalCode() {return new FieldInfo(Department.class,"externalCode",String.class, null,p);}
	    public final String externalCode="externalCode";
	    public Specification<T> externalCode(Op op,Object value) {return getExternalCode().where(op,value);}
	    public Specification<T> externalCode(Op op,Object value,boolean chkEmpty) {return getExternalCode().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getLegacyCode() {return new FieldInfo(Department.class,"legacyCode",String.class, null,p);}
	    public final String legacyCode="legacyCode";
	    public Specification<T> legacyCode(Op op,Object value) {return getLegacyCode().where(op,value);}
	    public Specification<T> legacyCode(Op op,Object value,boolean chkEmpty) {return getLegacyCode().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getCreatedBy() {return new FieldInfo(Department.class,"createdBy",String.class, null,p);}
	    public final String createdBy="createdBy";
	    public Specification<T> createdBy(Op op,Object value) {return getCreatedBy().where(op,value);}
	    public Specification<T> createdBy(Op op,Object value,boolean chkEmpty) {return getCreatedBy().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getCreateDate() {return new FieldInfo(Department.class,"createDate",java.time.LocalDateTime.class, null,p);}
	    public final String createDate="createDate";
	    public Specification<T> createDate(Op op,Object value) {return getCreateDate().where(op,value);}
	    public Specification<T> createDate(Op op,Object value,boolean chkEmpty) {return getCreateDate().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getLastModifiedBy() {return new FieldInfo(Department.class,"lastModifiedBy",String.class, null,p);}
	    public final String lastModifiedBy="lastModifiedBy";
	    public Specification<T> lastModifiedBy(Op op,Object value) {return getLastModifiedBy().where(op,value);}
	    public Specification<T> lastModifiedBy(Op op,Object value,boolean chkEmpty) {return getLastModifiedBy().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getLastModifiedDate() {return new FieldInfo(Department.class,"lastModifiedDate",java.time.LocalDateTime.class, null,p);}
	    public final String lastModifiedDate="lastModifiedDate";
	    public Specification<T> lastModifiedDate(Op op,Object value) {return getLastModifiedDate().where(op,value);}
	    public Specification<T> lastModifiedDate(Op op,Object value,boolean chkEmpty) {return getLastModifiedDate().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getVersion() {return new FieldInfo(Department.class,"version",Long.class, null,p);}
	    public final String version="version";
	    public Specification<T> version(Op op,Object value) {return getVersion().where(op,value);}
	    public Specification<T> version(Op op,Object value,boolean chkEmpty) {return getVersion().where(op,value,chkEmpty);}
	}
	
	public static $Skill<Skill> Skill=new $Skill<>();
	public static class $Skill<T> {
	
	    private FieldInfo p=null;
	    public $Skill() {}
	    public $Skill(FieldInfo p) {this.p=p;}
	    public static JoinSpec<Skill> joinSpec() {
	    	return new JoinSpec<>(Skill.class);
	    }
	    public static UpdateSpec<Skill> updateSpec() {
	    	return new UpdateSpec<>(Skill.class);
	    }
	    public static DeleteSpec<Skill> deleteSpec() {
	    	return new DeleteSpec<>(Skill.class);
	    }
	
	    public final FieldInfo<T> getSkillId() {return new FieldInfo(Skill.class,"skillId",Long.class, null,p);}
	    public final String skillId="skillId";
	    public Specification<T> skillId(Op op,Object value) {return getSkillId().where(op,value);}
	    public Specification<T> skillId(Op op,Object value,boolean chkEmpty) {return getSkillId().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getCategory() {return new FieldInfo(Skill.class,"category",String.class, null,p);}
	    public final String category="category";
	    public Specification<T> category(Op op,Object value) {return getCategory().where(op,value);}
	    public Specification<T> category(Op op,Object value,boolean chkEmpty) {return getCategory().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getSkillName() {return new FieldInfo(Skill.class,"skillName",String.class, null,p);}
	    public final String skillName="skillName";
	    public Specification<T> skillName(Op op,Object value) {return getSkillName().where(op,value);}
	    public Specification<T> skillName(Op op,Object value,boolean chkEmpty) {return getSkillName().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getProficiency() {return new FieldInfo(Skill.class,"proficiency",Integer.class, null,p);}
	    public final String proficiency="proficiency";
	    public Specification<T> proficiency(Op op,Object value) {return getProficiency().where(op,value);}
	    public Specification<T> proficiency(Op op,Object value,boolean chkEmpty) {return getProficiency().where(op,value,chkEmpty);}
	}
	
	public static $Employee<Employee> Employee=new $Employee<>();
	public static class $Employee<T> {
	
	    private FieldInfo p=null;
	    public $Employee() {}
	    public $Employee(FieldInfo p) {this.p=p;}
	    public static JoinSpec<Employee> joinSpec() {
	    	return new JoinSpec<>(Employee.class);
	    }
	    public static UpdateSpec<Employee> updateSpec() {
	    	return new UpdateSpec<>(Employee.class);
	    }
	    public static DeleteSpec<Employee> deleteSpec() {
	    	return new DeleteSpec<>(Employee.class);
	    }
	
	    public final FieldInfo<T> getEmpId() {return new FieldInfo(Employee.class,"empId",String.class, null,p);}
	    public final String empId="empId";
	    public Specification<T> empId(Op op,Object value) {return getEmpId().where(op,value);}
	    public Specification<T> empId(Op op,Object value,boolean chkEmpty) {return getEmpId().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getFirstName() {return new FieldInfo(Employee.class,"firstName",String.class, null,p);}
	    public final String firstName="firstName";
	    public Specification<T> firstName(Op op,Object value) {return getFirstName().where(op,value);}
	    public Specification<T> firstName(Op op,Object value,boolean chkEmpty) {return getFirstName().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getLastName() {return new FieldInfo(Employee.class,"lastName",String.class, null,p);}
	    public final String lastName="lastName";
	    public Specification<T> lastName(Op op,Object value) {return getLastName().where(op,value);}
	    public Specification<T> lastName(Op op,Object value,boolean chkEmpty) {return getLastName().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getEmail() {return new FieldInfo(Employee.class,"email",String.class, null,p);}
	    public final String email="email";
	    public Specification<T> email(Op op,Object value) {return getEmail().where(op,value);}
	    public Specification<T> email(Op op,Object value,boolean chkEmpty) {return getEmail().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getPhone() {return new FieldInfo(Employee.class,"phone",String.class, null,p);}
	    public final String phone="phone";
	    public Specification<T> phone(Op op,Object value) {return getPhone().where(op,value);}
	    public Specification<T> phone(Op op,Object value,boolean chkEmpty) {return getPhone().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getHireDate() {return new FieldInfo(Employee.class,"hireDate",java.time.LocalDate.class, null,p);}
	    public final String hireDate="hireDate";
	    public Specification<T> hireDate(Op op,Object value) {return getHireDate().where(op,value);}
	    public Specification<T> hireDate(Op op,Object value,boolean chkEmpty) {return getHireDate().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getSalary() {return new FieldInfo(Employee.class,"salary",java.math.BigDecimal.class, null,p);}
	    public final String salary="salary";
	    public Specification<T> salary(Op op,Object value) {return getSalary().where(op,value);}
	    public Specification<T> salary(Op op,Object value,boolean chkEmpty) {return getSalary().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getBonusRate() {return new FieldInfo(Employee.class,"bonusRate",Double.class, null,p);}
	    public final String bonusRate="bonusRate";
	    public Specification<T> bonusRate(Op op,Object value) {return getBonusRate().where(op,value);}
	    public Specification<T> bonusRate(Op op,Object value,boolean chkEmpty) {return getBonusRate().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getActive() {return new FieldInfo(Employee.class,"active",Boolean.class, null,p);}
	    public final String active="active";
	    public Specification<T> active(Op op,Object value) {return getActive().where(op,value);}
	    public Specification<T> active(Op op,Object value,boolean chkEmpty) {return getActive().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getDepartment() {return new FieldInfo(Employee.class,"department",Department.class, null,p);}
	    public final String department="department";
	    public final $Department<T> department() {return new $Department(getDepartment());}
	
	    public final FieldInfo<T> getSkills() {return new FieldInfo(Employee.class,"skills",Skill.class, List.class,p);}
	    public final String skills="skills";
	
	    public final FieldInfo<T> getManager() {return new FieldInfo(Employee.class,"manager",Employee.class, null,p);}
	    public final String manager="manager";
	    public final $Employee<T> manager() {return new $Employee(getManager());}
	
	    public final FieldInfo<T> getDirectReports() {return new FieldInfo(Employee.class,"directReports",Employee.class, List.class,p);}
	    public final String directReports="directReports";
	
	    public final FieldInfo<T> getCreatedBy() {return new FieldInfo(Employee.class,"createdBy",String.class, null,p);}
	    public final String createdBy="createdBy";
	    public Specification<T> createdBy(Op op,Object value) {return getCreatedBy().where(op,value);}
	    public Specification<T> createdBy(Op op,Object value,boolean chkEmpty) {return getCreatedBy().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getCreateDate() {return new FieldInfo(Employee.class,"createDate",java.time.LocalDateTime.class, null,p);}
	    public final String createDate="createDate";
	    public Specification<T> createDate(Op op,Object value) {return getCreateDate().where(op,value);}
	    public Specification<T> createDate(Op op,Object value,boolean chkEmpty) {return getCreateDate().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getLastModifiedBy() {return new FieldInfo(Employee.class,"lastModifiedBy",String.class, null,p);}
	    public final String lastModifiedBy="lastModifiedBy";
	    public Specification<T> lastModifiedBy(Op op,Object value) {return getLastModifiedBy().where(op,value);}
	    public Specification<T> lastModifiedBy(Op op,Object value,boolean chkEmpty) {return getLastModifiedBy().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getLastModifiedDate() {return new FieldInfo(Employee.class,"lastModifiedDate",java.time.LocalDateTime.class, null,p);}
	    public final String lastModifiedDate="lastModifiedDate";
	    public Specification<T> lastModifiedDate(Op op,Object value) {return getLastModifiedDate().where(op,value);}
	    public Specification<T> lastModifiedDate(Op op,Object value,boolean chkEmpty) {return getLastModifiedDate().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getVersion() {return new FieldInfo(Employee.class,"version",Long.class, null,p);}
	    public final String version="version";
	    public Specification<T> version(Op op,Object value) {return getVersion().where(op,value);}
	    public Specification<T> version(Op op,Object value,boolean chkEmpty) {return getVersion().where(op,value,chkEmpty);}
	}
	
	public static $ProjectAssignment<ProjectAssignment> ProjectAssignment=new $ProjectAssignment<>();
	public static class $ProjectAssignment<T> {
	
	    private FieldInfo p=null;
	    public $ProjectAssignment() {}
	    public $ProjectAssignment(FieldInfo p) {this.p=p;}
	    public static JoinSpec<ProjectAssignment> joinSpec() {
	    	return new JoinSpec<>(ProjectAssignment.class);
	    }
	    public static UpdateSpec<ProjectAssignment> updateSpec() {
	    	return new UpdateSpec<>(ProjectAssignment.class);
	    }
	    public static DeleteSpec<ProjectAssignment> deleteSpec() {
	    	return new DeleteSpec<>(ProjectAssignment.class);
	    }
	
	    public final FieldInfo<T> getEmpId() {return new FieldInfo(ProjectAssignment.class,"empId",String.class, null,p);}
	    public final String empId="empId";
	    public Specification<T> empId(Op op,Object value) {return getEmpId().where(op,value);}
	    public Specification<T> empId(Op op,Object value,boolean chkEmpty) {return getEmpId().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getProjectCode() {return new FieldInfo(ProjectAssignment.class,"projectCode",String.class, null,p);}
	    public final String projectCode="projectCode";
	    public Specification<T> projectCode(Op op,Object value) {return getProjectCode().where(op,value);}
	    public Specification<T> projectCode(Op op,Object value,boolean chkEmpty) {return getProjectCode().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getRole() {return new FieldInfo(ProjectAssignment.class,"role",String.class, null,p);}
	    public final String role="role";
	    public Specification<T> role(Op op,Object value) {return getRole().where(op,value);}
	    public Specification<T> role(Op op,Object value,boolean chkEmpty) {return getRole().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getAssignDate() {return new FieldInfo(ProjectAssignment.class,"assignDate",java.time.LocalDate.class, null,p);}
	    public final String assignDate="assignDate";
	    public Specification<T> assignDate(Op op,Object value) {return getAssignDate().where(op,value);}
	    public Specification<T> assignDate(Op op,Object value,boolean chkEmpty) {return getAssignDate().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getHoursAllocated() {return new FieldInfo(ProjectAssignment.class,"hoursAllocated",Integer.class, null,p);}
	    public final String hoursAllocated="hoursAllocated";
	    public Specification<T> hoursAllocated(Op op,Object value) {return getHoursAllocated().where(op,value);}
	    public Specification<T> hoursAllocated(Op op,Object value,boolean chkEmpty) {return getHoursAllocated().where(op,value,chkEmpty);}
	}
	
	public static $Project<Project> Project=new $Project<>();
	public static class $Project<T> {
	
	    private FieldInfo p=null;
	    public $Project() {}
	    public $Project(FieldInfo p) {this.p=p;}
	    public static JoinSpec<Project> joinSpec() {
	    	return new JoinSpec<>(Project.class);
	    }
	    public static UpdateSpec<Project> updateSpec() {
	    	return new UpdateSpec<>(Project.class);
	    }
	    public static DeleteSpec<Project> deleteSpec() {
	    	return new DeleteSpec<>(Project.class);
	    }
	
	    public final FieldInfo<T> getProjectCode() {return new FieldInfo(Project.class,"projectCode",String.class, null,p);}
	    public final String projectCode="projectCode";
	    public Specification<T> projectCode(Op op,Object value) {return getProjectCode().where(op,value);}
	    public Specification<T> projectCode(Op op,Object value,boolean chkEmpty) {return getProjectCode().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getName() {return new FieldInfo(Project.class,"name",String.class, null,p);}
	    public final String name="name";
	    public Specification<T> name(Op op,Object value) {return getName().where(op,value);}
	    public Specification<T> name(Op op,Object value,boolean chkEmpty) {return getName().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getStartDate() {return new FieldInfo(Project.class,"startDate",java.time.LocalDate.class, null,p);}
	    public final String startDate="startDate";
	    public Specification<T> startDate(Op op,Object value) {return getStartDate().where(op,value);}
	    public Specification<T> startDate(Op op,Object value,boolean chkEmpty) {return getStartDate().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getEndDate() {return new FieldInfo(Project.class,"endDate",java.time.LocalDate.class, null,p);}
	    public final String endDate="endDate";
	    public Specification<T> endDate(Op op,Object value) {return getEndDate().where(op,value);}
	    public Specification<T> endDate(Op op,Object value,boolean chkEmpty) {return getEndDate().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getBudget() {return new FieldInfo(Project.class,"budget",java.math.BigDecimal.class, null,p);}
	    public final String budget="budget";
	    public Specification<T> budget(Op op,Object value) {return getBudget().where(op,value);}
	    public Specification<T> budget(Op op,Object value,boolean chkEmpty) {return getBudget().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getStatus() {return new FieldInfo(Project.class,"status",ProjectStatus.class, null,p);}
	    public final String status="status";
	    public Specification<T> status(Op op,Object value) {return getStatus().where(op,value);}
	    public Specification<T> status(Op op,Object value,boolean chkEmpty) {return getStatus().where(op,value,chkEmpty);}
	
	    public final FieldInfo<T> getLead() {return new FieldInfo(Project.class,"lead",Employee.class, null,p);}
	    public final String lead="lead";
	    public final $Employee<T> lead() {return new $Employee(getLead());}
	}
	
}
