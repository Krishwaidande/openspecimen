package com.krishagni.catissueplus.core.biospecimen.repository.impl;


import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Junction;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.hibernate.sql.JoinType;

import com.krishagni.catissueplus.core.administrative.domain.Site;
import com.krishagni.catissueplus.core.biospecimen.domain.CollectionProtocol;
import com.krishagni.catissueplus.core.biospecimen.repository.SpecimenListCriteria;
import com.krishagni.catissueplus.core.common.access.SiteCpPair;
import com.krishagni.catissueplus.core.common.events.Resource;

public class BiospecimenDaoHelper {

	private static final BiospecimenDaoHelper instance = new BiospecimenDaoHelper();

	private BiospecimenDaoHelper() {
	}

	public static BiospecimenDaoHelper getInstance() {
		return instance;
	}

	public void addSiteCpsCond(Criteria query, SpecimenListCriteria crit) {
		addSiteCpsCond(query, crit, true);
	}

	public void addSiteCpsCond(Criteria query, SpecimenListCriteria crit, boolean spmnList) {
		addSiteCpsCond(query, crit.siteCps(), crit.useMrnSites(), query.getAlias().equals("visit") ? "cpr" : "visit", spmnList);
	}

	public void addSiteCpsCond(Criteria query, Collection<SiteCpPair> siteCps, boolean useMrnSites, String startAlias) {
		addSiteCpsCond(query, siteCps, useMrnSites, startAlias, true);
	}

	public void addSiteCpsCond(Criteria query, Collection<SiteCpPair> siteCps, boolean useMrnSites, String startAlias, boolean spmnList) {
		if (CollectionUtils.isEmpty(siteCps)) {
			return;
		}

		switch (startAlias) {
			case "visit":
				query.createAlias("specimen.visit", "visit");

			case "cpr":
				query.createAlias("visit.registration", "cpr");

			case "cp":
				query.createAlias("cpr.collectionProtocol", "cp");
		}

		query.createAlias("cp.sites", "cpSite")
			.createAlias("cpSite.site", "site")
			.createAlias("cpr.participant", "participant")
			.createAlias("participant.pmis", "pmi", JoinType.LEFT_OUTER_JOIN)
			.createAlias("pmi.site", "mrnSite", JoinType.LEFT_OUTER_JOIN);

		Disjunction mainCond = Restrictions.disjunction();
		Map<String, Set<SiteCpPair>> siteCpsMap = SiteCpPair.segregateByResources(siteCps);
		for (Map.Entry<String, Set<SiteCpPair>> siteCpEntry : siteCpsMap.entrySet()) {
			Junction siteCpsCond = getSiteCpsCond(siteCpEntry.getValue(), useMrnSites);
			if (spmnList && Resource.PRIMARY_SPECIMEN.getName().equals(siteCpEntry.getKey())) {
				mainCond.add(
					Restrictions.and(
						Restrictions.eq("specimen.lineage", "New"),
						siteCpsCond
					)
				);
			} else {
				mainCond.add(siteCpsCond);
			}
		}

		query.add(mainCond);
	}

	public String getSiteCpsCondAqlForCps(Collection<SiteCpPair> siteCps) {
		if (siteCps == null || siteCps.isEmpty()) {
			return StringUtils.EMPTY;
		}

		StringBuilder aql = new StringBuilder();
		for (SiteCpPair siteCp : siteCps) {
			String restriction = getAqlSiteIdRestriction("CollectionProtocol.cpSites.siteId", siteCp);
			if (aql.length() > 0) {
				aql.append(" or ");
			}

			aql.append(restriction);
		}

		if (aql.length() > 0) {
			aql.insert(0, "(").append(")");
		}

		return aql.toString();
	}

	public String getSiteCpsCondAql(Collection<SiteCpPair> siteCps, boolean useMrnSites) {
		if (CollectionUtils.isEmpty(siteCps)) {
			return StringUtils.EMPTY;
		}

		Map<String, Set<SiteCpPair>> siteCpsByResources = SiteCpPair.segregateByResources(siteCps);
		StringBuilder aql = new StringBuilder();
		for (Map.Entry<String, Set<SiteCpPair>> siteCpsEntry : siteCpsByResources.entrySet()) {
			String restriction = getSiteCpsCondAql0(siteCpsEntry.getValue(), useMrnSites);
			if (Resource.PRIMARY_SPECIMEN.getName().equals(siteCpsEntry.getKey())) {
				restriction = "(Specimen.lineage = \"New\" and " + restriction + ")";
			}

			if (aql.length() > 0) {
				aql.append(" or ");
			}

			aql.append(restriction);
		}

		if (aql.length() > 0) {
			aql.insert(0, "(").append(")");
		}

		return aql.toString();
	}

	public DetachedCriteria getCpIdsFilter(Collection<SiteCpPair> siteCps) {
		DetachedCriteria filter = DetachedCriteria.forClass(CollectionProtocol.class, "cp")
			.setProjection(Projections.distinct(Projections.property("cp.id")));

		boolean siteAdded = false, instAdded = false;
		Disjunction orCond = Restrictions.disjunction();
		for (SiteCpPair siteCp : siteCps) {
			if (siteCp.getCpId() != null) {
				orCond.add(Restrictions.eq("cp.id", siteCp.getCpId()));
			} else {
				if (!siteAdded) {
					filter.createAlias("cp.sites", "cpSite")
						.createAlias("cpSite.site", "site");
					siteAdded = true;
				}

				if (siteCp.getSiteId() != null) {
					orCond.add(Restrictions.eq("site.id", siteCp.getSiteId()));
				} else {
					if (!instAdded) {
						filter.createAlias("site.institute", "institute");
						instAdded = true;
					}

					orCond.add(Restrictions.eq("institute.id", siteCp.getInstituteId()));
				}
			}
		}

		return filter.add(orCond);
	}

	private Junction getSiteCpsCond(Collection<SiteCpPair> siteCps, boolean useMrnSites) {
		Disjunction cpSitesCond = Restrictions.disjunction();
		for (SiteCpPair siteCp : siteCps) {
			Junction siteCond = Restrictions.disjunction();
			if (useMrnSites) {
				//
				// When MRNs exist, site ID should be one of the MRN site
				//
				Junction mrnSite = Restrictions.conjunction()
					.add(Restrictions.isNotEmpty("participant.pmis"))
					.add(getSiteIdRestriction("mrnSite.id", siteCp));

				//
				// When no MRNs exist, site ID should be one of CP site
				//
				Junction cpSite = Restrictions.conjunction()
					.add(Restrictions.isEmpty("participant.pmis"))
					.add(getSiteIdRestriction("site.id", siteCp));

				siteCond.add(mrnSite).add(cpSite);
			} else {
				//
				// Site ID should be either MRN site or CP site
				//
				siteCond
					.add(getSiteIdRestriction("mrnSite.id", siteCp))
					.add(getSiteIdRestriction("site.id", siteCp));
			}

			Junction cond = Restrictions.conjunction().add(siteCond);
			if (siteCp.getCpId() != null) {
				cond.add(Restrictions.eq("cp.id", siteCp.getCpId()));
			}

			cpSitesCond.add(cond);
		}

		return cpSitesCond;
	}

	private String getSiteCpsCondAql0(Collection<SiteCpPair> siteCps, boolean useMrnSites) {
		if (CollectionUtils.isEmpty(siteCps)) {
			return StringUtils.EMPTY;
		}

		Set<String> cpSitesCond = new LinkedHashSet<>(); // joined by or
		for (SiteCpPair siteCp : siteCps) {
			List<String> siteCond = new ArrayList<>(); // joined by or
			if (useMrnSites) {
				//
				// When MRNs exist, site ID should be one of the MRN site
				//
				String mrnSite =
					"Participant.medicalRecord.mrnSiteId exists and " +
					getAqlSiteIdRestriction("Participant.medicalRecord.mrnSiteId", siteCp);
				siteCond.add(mrnSite);

				//
				// When no MRNs exist, site ID should be one of CP site
				//
				String cpSite =
					"Participant.medicalRecord.mrnSiteId not exists and " +
					getAqlSiteIdRestriction("CollectionProtocol.cpSites.siteId", siteCp);
				siteCond.add(cpSite);
			} else {
				//
				// Site ID should be either MRN site or CP site
				//
				siteCond.add(getAqlSiteIdRestriction("Participant.medicalRecord.mrnSiteId", siteCp));
				siteCond.add(getAqlSiteIdRestriction("CollectionProtocol.cpSites.siteId", siteCp));
			}

			String cond = "(" + StringUtils.join(siteCond, " or ") + ")";
			if (siteCp.getCpId() != null) {
				cond += " and CollectionProtocol.id = " + siteCp.getCpId();
			}

			cpSitesCond.add("(" + cond + ")");
		}

		return "(" + StringUtils.join(cpSitesCond, " or ") + ")";
	}

	private Criterion getSiteIdRestriction(String property, SiteCpPair siteCp) {
		if (siteCp.getSiteId() != null) {
			return Restrictions.eq(property, siteCp.getSiteId());
		}

		DetachedCriteria subQuery = DetachedCriteria.forClass(Site.class)
			.add(Restrictions.eq("institute.id", siteCp.getInstituteId()))
			.setProjection(Projections.property("id"));
		return Subqueries.propertyIn(property, subQuery);
	}

	private String getAqlSiteIdRestriction(String property, SiteCpPair siteCp) {
		if (siteCp.getSiteId() != null) {
			return property + " = " + siteCp.getSiteId();
		} else {
			return property + " in " + sql(String.format(INSTITUTE_SITE_IDS_SQL, siteCp.getInstituteId()));
		}
	}

	private String sql(String sql) {
		return "sql(\"" + sql + "\")";
	}

	private static final String INSTITUTE_SITE_IDS_SQL =
		"select identifier from catissue_site where institute_id = %d and activity_status != 'Disabled'";
}
