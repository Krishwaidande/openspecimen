
package com.krishagni.catissueplus.core.administrative.domain;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.krishagni.catissueplus.core.administrative.domain.factory.DistributionProtocolErrorCode;
import com.krishagni.catissueplus.core.biospecimen.domain.BaseEntity;
import com.krishagni.catissueplus.core.common.CollectionUpdater;
import com.krishagni.catissueplus.core.common.errors.OpenSpecimenException;
import com.krishagni.catissueplus.core.common.events.DependentEntityDetail;
import com.krishagni.catissueplus.core.common.util.Status;
import com.krishagni.catissueplus.core.common.util.Utility;
import com.krishagni.catissueplus.core.de.domain.SavedQuery;

public class DistributionProtocol extends BaseEntity {
	private static final String ENTITY_NAME = "distribution_protocol";

	private Institute institute;
	
	private Site defReceivingSite;

	private User principalInvestigator;

	private String title;

	private String shortTitle;

	private String irbId;

	private Date startDate;
	
	private Date endDate;

	private String activityStatus;
	
	private SavedQuery report;
	
	private Set<DistributionOrder> distributionOrders = new HashSet<DistributionOrder>();
	
	private Set<DpDistributionSite> distributingSites = new HashSet<DpDistributionSite>();
	
	private Set<DpRequirement> requirements = new HashSet<DpRequirement>();
	
	public static String getEntityName() {
		return ENTITY_NAME;
	}
	
	public Institute getInstitute() {
		return institute;
	}

	public void setInstitute(Institute institute) {
		this.institute = institute;
	}
	
	public Site getDefReceivingSite() {
		return defReceivingSite;
	}
	
	public void setDefReceivingSite(Site defReceivingSite) {
		this.defReceivingSite = defReceivingSite;
	}

	public User getPrincipalInvestigator() {
		return principalInvestigator;
	}

	public void setPrincipalInvestigator(User principalInvestigator) {
		this.principalInvestigator = principalInvestigator;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getShortTitle() {
		return shortTitle;
	}

	public void setShortTitle(String shortTitle) {
		this.shortTitle = shortTitle;
	}

	public String getIrbId() {
		return irbId;
	}

	public void setIrbId(String irbId) {
		this.irbId = irbId;
	}

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public String getActivityStatus() {
		return activityStatus;
	}

	public void setActivityStatus(String activityStatus) {
		this.activityStatus = activityStatus;
	}

	public SavedQuery getReport() {
		return report;
	}

	public void setReport(SavedQuery report) {
		this.report = report;
	}

	public Set<DistributionOrder> getDistributionOrders() {
		return distributionOrders;
	}

	public void setDistributionOrders(Set<DistributionOrder> distributionOrders) {
		this.distributionOrders = distributionOrders;
	}
	
	public Set<DpDistributionSite> getDistributingSites() {
		return distributingSites;
	}
	
	public void setDistributingSites(Set<DpDistributionSite> distributingSites) {
		this.distributingSites = distributingSites;
	}

	public Set<DpRequirement> getRequirements() {
		return requirements;
	}

	public void setRequirements(Set<DpRequirement> requirements) {
		this.requirements = requirements;
	}

	public void update(DistributionProtocol distributionProtocol) {
		if (distributionProtocol.getActivityStatus().equals(Status.ACTIVITY_STATUS_DISABLED.getStatus())) {
			setShortTitle(Utility.getDisabledValue(distributionProtocol.getShortTitle(), 50));
			setTitle(Utility.getDisabledValue(distributionProtocol.getTitle(), 255));
		}
		else {
			setShortTitle(distributionProtocol.getShortTitle());
			setTitle(distributionProtocol.getTitle());
		}
		setIrbId(distributionProtocol.getIrbId());
		setInstitute(distributionProtocol.getInstitute());
		setDefReceivingSite(distributionProtocol.getDefReceivingSite());
		setPrincipalInvestigator(distributionProtocol.getPrincipalInvestigator());
		setStartDate(distributionProtocol.getStartDate());
		setEndDate(distributionProtocol.getEndDate());
		setActivityStatus(distributionProtocol.getActivityStatus());
		setReport(distributionProtocol.getReport());
		CollectionUpdater.update(getDistributingSites(), distributionProtocol.getDistributingSites());
		//CollectionUpdater.update(getRequirements(), distributionProtocol.getRequirements());
	}
	
	public List<DependentEntityDetail> getDependentEntities() {
		return DependentEntityDetail
				.singletonList(DistributionOrder.getEntityName(), getDistributionOrders().size());
	}
	
	public void delete() {
		List<DependentEntityDetail> dependentEntities = getDependentEntities();
		if (!dependentEntities.isEmpty()) {
			throw OpenSpecimenException.userError(DistributionProtocolErrorCode.REF_ENTITY_FOUND);
		}
		
		setShortTitle(Utility.getDisabledValue(getShortTitle(), 50));
		setTitle(Utility.getDisabledValue(getTitle(), 255));
		setActivityStatus(Status.ACTIVITY_STATUS_DISABLED.getStatus());
	}
	
	public Set<Site> getAllDistributingSites() {
		Set<Site> sites = new HashSet<Site>();
		for (DpDistributionSite distSite : getDistributingSites()) {
			if (distSite.getSite() != null) {
				sites.add(distSite.getSite());
			} else {
				sites.addAll(distSite.getInstitute().getSites());
			}
		}
		
		return sites;
	}
	
}
