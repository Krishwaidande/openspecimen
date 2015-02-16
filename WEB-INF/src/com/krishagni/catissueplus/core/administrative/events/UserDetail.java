
package com.krishagni.catissueplus.core.administrative.events;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.krishagni.catissueplus.core.administrative.domain.Site;
import com.krishagni.catissueplus.core.administrative.domain.User;

public class UserDetail {

	private Long id;

	private String lastName;

	private String firstName;

	private String domainName;

	private String emailAddress;

	private String loginName;

	private List<String> sitesName = new ArrayList<String>();

	private Date createDate;

	private String activityStatus;

    private String instituteName;

	private String deptName;

    private Boolean superAdmin;

    private String address;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getDomainName() {
		return domainName;
	}

	public void setDomainName(String domainName) {
		this.domainName = domainName;
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}

	public String getLoginName() {
		return loginName;
	}

	public void setLoginName(String loginName) {
		this.loginName = loginName;
	}

	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}

	public String getActivityStatus() {
		return activityStatus;
	}

	public void setActivityStatus(String activityStatus) {
		this.activityStatus = activityStatus;
	}

    public String getInstituteName() { 
    	return instituteName; 
    }

    public void setInstituteName(String instituteName) { 
    	this.instituteName = instituteName; 
    }

	public String getDeptName() {
		return deptName;
	}

	public void setDeptName(String deptName) {
		this.deptName = deptName;
	}

    public Boolean getSuperAdmin() { 
    	return superAdmin; 
    }

    public void setSuperAdmin(Boolean superAdmin) { 
    	this.superAdmin = superAdmin; 
    }

    public String getAddress() { 
    	return address;
    }

    public void setAddress(String address) { 
    	this.address = address; 
    }

	public List<String> getSitesName() {
		return sitesName;
	}

	public void setSitesName(List<String> sitesName) {
		this.sitesName = sitesName;
	}

	public static UserDetail from(User user) {
		UserDetail userDto = new UserDetail();
		userDto.setId(user.getId());
		userDto.setLoginName(user.getLoginName());
		userDto.setFirstName(user.getFirstName());
		userDto.setLastName(user.getLastName());
		userDto.setCreateDate(user.getCreateDate());
		userDto.setActivityStatus(user.getActivityStatus());
		userDto.setEmailAddress(user.getEmailAddress());
		userDto.setDomainName(user.getAuthDomain().getName());
		userDto.setDeptName(user.getDepartment().getName());
		setUserSiteNames(userDto, user.getSites());	

		return userDto;
	}

	private static void setUserSiteNames(UserDetail userDto, Set<Site> sites) {
		List<String> siteNames = new ArrayList<String>();
		for (Site site : sites) {
			siteNames.add(site.getName());
		}
		userDto.setSitesName(siteNames);
	}
}
