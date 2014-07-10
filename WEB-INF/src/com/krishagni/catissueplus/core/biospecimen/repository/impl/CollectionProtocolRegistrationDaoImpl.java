
package com.krishagni.catissueplus.core.biospecimen.repository.impl;

import edu.wustl.catissuecore.domain.CollectionProtocolEvent;
import edu.wustl.common.util.XMLPropertyHandler;
import gov.nih.nci.logging.api.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.math.NumberUtils;
import org.hibernate.Query;
import org.springframework.stereotype.Repository;

import com.krishagni.catissueplus.core.biospecimen.domain.CollectionProtocolRegistration;
import com.krishagni.catissueplus.core.biospecimen.domain.SpecimenCollectionGroup;
import com.krishagni.catissueplus.core.biospecimen.events.ParticipantInfo;
import com.krishagni.catissueplus.core.biospecimen.events.SpecimenCollectionGroupInfo;
import com.krishagni.catissueplus.core.biospecimen.repository.CollectionProtocolRegistrationDao;
import com.krishagni.catissueplus.core.common.repository.AbstractDao;
import com.krishagni.catissueplus.core.common.util.Status;
import com.krishagni.catissueplus.core.common.util.Utility;

@Repository("collectionProtocolRegistrationDao")
public class CollectionProtocolRegistrationDaoImpl extends AbstractDao<CollectionProtocolRegistration>
		implements
			CollectionProtocolRegistrationDao {

	@SuppressWarnings("unchecked")
	@Override
	public List<ParticipantInfo> getParticipants(Long cpId, String searchString) {
		boolean isSearchTermSpecified = !StringUtils.isBlank(searchString);

		String queryName = GET_PARTICIPANTS_BY_CP_ID;
		if (isSearchTermSpecified) {
			queryName = GET_PARTICIPANTS_BY_CP_ID_AND_SEARCH_TERM;
		}
		
		Query query = sessionFactory.getCurrentSession().getNamedQuery(queryName);
		query.setLong("cpId", cpId);

		if (isSearchTermSpecified) {
			query.setString("searchTerm", searchString.toLowerCase() + "%");
		}
		query.setMaxResults(Utility.getMaxParticipantCnt());
		List<ParticipantInfo> result = new ArrayList<ParticipantInfo>();
		List<Object[]> rows = query.list();
		for (Object[] row : rows) {
			ParticipantInfo participant = new ParticipantInfo();
			participant.setCprId((Long) row[0]);
			participant.setId((Long) row[1]);
			participant.setPpId((String) row[2]);
			participant.setFirstName((String) row[3]);
			participant.setLastName((String) row[4]);

			result.add(participant);
		}

		return result;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public List<ParticipantInfo> getPhiParticipants(Long cpId, String searchString) {
		boolean isSearchTermSpecified = !StringUtils.isBlank(searchString);

		String queryName = GET_PHI_PARTICIPANTS_BY_CP_ID;
		if (isSearchTermSpecified) {
			queryName = GET_PHI_PARTICIPANTS_BY_CP_ID_AND_SEARCH_TERM;
		}
		

		Query query = sessionFactory.getCurrentSession().getNamedQuery(queryName);
		query.setLong("cpId", cpId);
		if (isSearchTermSpecified) {
			query.setString("searchTerm", searchString.toLowerCase() + "%");
		}
		query.setMaxResults(Utility.getMaxParticipantCnt());
		List<ParticipantInfo> result = new ArrayList<ParticipantInfo>();
		List<Object[]> rows = query.list();
		for (Object[] row : rows) {
			ParticipantInfo participant = new ParticipantInfo();
			participant.setCprId((Long) row[0]);
			participant.setId((Long) row[1]);
			participant.setPpId((String) row[2]);
			participant.setFirstName((String) row[3]);
			participant.setLastName((String) row[4]);

			result.add(participant);
		}

		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<SpecimenCollectionGroupInfo> getScgList(Long cprId) {

		CollectionProtocolRegistration cpr = (CollectionProtocolRegistration) sessionFactory.getCurrentSession().get(
				CollectionProtocolRegistration.class, cprId);
		Set<SpecimenCollectionGroupInfo> scgsInfo = new HashSet<SpecimenCollectionGroupInfo>();
		Collection<SpecimenCollectionGroup> groups = cpr.getScgCollection();
		for (SpecimenCollectionGroup specimenCollectionGroup : groups) {
			if(!Status.ACTIVITY_STATUS_DISABLED.getStatus().equals(specimenCollectionGroup.getActivityStatus())){
			scgsInfo.add(SpecimenCollectionGroupInfo.fromScg(specimenCollectionGroup, cpr.getRegistrationDate()));
			}
		}
		Collection<CollectionProtocolEvent> cpes = cpr.getCollectionProtocol().getCollectionProtocolEventCollection();
		for (CollectionProtocolEvent collectionProtocolEvent : cpes) {
			if(!Status.ACTIVITY_STATUS_DISABLED.getStatus().equals(collectionProtocolEvent.getActivityStatus())){
			scgsInfo.add(SpecimenCollectionGroupInfo.fromCpe(collectionProtocolEvent, cpr.getRegistrationDate()));
			}
		}

		//				" ";
		//				"scg.collectionProtocolEvent cpe " +
		//				"where cpr.id=:cprId";
		//		Query query = sessionFactory.getCurrentSession().getNamedQuery(GET_COLLECTION_GROUPSBY_CPR_ID);
		//		Query query = sessionFactory.getCurrentSession().createQuery(hhhh);
		//		
		//		query.setLong("cprId", cprId);
		////		query.setLong("cprId2", cprId);
		//		List<Object[]> results = query.list();
		//		
		//Query query1 = sessionFactory.getCurrentSession().createQuery(hql3);
		//		
		//		query.setLong("cprId", cprId);
		//		List<Object[]> results1 = query.list();
		//
		////		List<SpecimenCollectionGroupInfo> scgs = new ArrayList<SpecimenCollectionGroupInfo>();
		//		for (Object[] object : results) {
		//			SpecimenCollectionGroupInfo scg = new SpecimenCollectionGroupInfo();
		//			scg.setId(object[0]==null?0l:Long.valueOf(object[0].toString()));
		//			scg.setName(object[1] == null ? "" : object[1].toString());
		//			scg.setCollectionStatus(object[2]==null?"pending":object[2].toString());
		//			if (object[3] != null) {
		//				scg.setReceivedDate((Date) object[7]);
		//			}
		//			scg.setEventPoint(Double.parseDouble(object[5].toString()));
		//			scg.setCollectionPointLabel(object[6].toString());
		////			scg.setRegistrationDate((Date) object[7]);
		//			scgs.add(scg);
		//		}
		List<SpecimenCollectionGroupInfo> list = new ArrayList<SpecimenCollectionGroupInfo>(scgsInfo);
		if (list != null) {
			Collections.sort(list);
		}
		return list;
	}

	@Override
	public boolean isBacodeUnique(String barcode) {
		Query query = sessionFactory.getCurrentSession().getNamedQuery(GET_CPR_ID_BY_BARCODE);
		query.setString("barcode", barcode);
		return query.list().isEmpty() ? true : false;
	}

	@Override
	public CollectionProtocolRegistration getCpr(Long cprId) {

		return (CollectionProtocolRegistration) sessionFactory.getCurrentSession().get(
				CollectionProtocolRegistration.class, cprId);
	}

	@Override
	@SuppressWarnings("unchecked")
	public CollectionProtocolRegistration getCprByPpId(Long cpId, String protocolParticipantIdentifier) {

		Query query = sessionFactory.getCurrentSession().getNamedQuery(GET_CPR_BY_PPID_AND_CPID);
		query.setString("ppid", protocolParticipantIdentifier);
		query.setLong("cpId", cpId);
		List<CollectionProtocolRegistration> result = query.list();
		return result.isEmpty() ? null : result.get(0);
	}

	@Override
	public boolean isPpidUniqueForProtocol(Long cpId, String protocolParticipantIdentifier) {
		Query query = sessionFactory.getCurrentSession().getNamedQuery(GET_CPID_BY_PPID_AND_CPID);
		query.setString("ppid", protocolParticipantIdentifier);
		query.setLong("cpId", cpId);
		boolean isUnique = query.list().isEmpty() ? true : false;
		return isUnique;
	}

	private static final String FQN = CollectionProtocolRegistration.class.getName();

	private static final String GET_PARTICIPANTS_BY_CP_ID = FQN + ".getParticipantsByCpId";

	private static final String GET_PARTICIPANTS_BY_CP_ID_AND_SEARCH_TERM = FQN + ".getParticipantsByCpIdAndSearchTerm";
	
	private static final String GET_PHI_PARTICIPANTS_BY_CP_ID = FQN + ".getPhiParticipantsByCpId";

	private static final String GET_PHI_PARTICIPANTS_BY_CP_ID_AND_SEARCH_TERM = FQN + ".getPhiParticipantsByCpIdAndSearchTerm";

	private static final String GET_COLLECTION_GROUPSBY_CPR_ID = FQN + ".getCollectionGroupsByCprId";

	private static final String GET_CPR_ID_BY_BARCODE = FQN + ".getCprIdByBarcode";

	private static final String GET_CPID_BY_PPID_AND_CPID = FQN + ".getCprIdByPpid";

	private static final String GET_CPR_BY_PPID_AND_CPID = FQN + ".getCprByPpid";

	//	private final String hql = "select scg.id,scg.name,scg.collectionStatus, scg.receivedTimestamp, "
	//			+ "scg.collectionProtocolEvent.id,scg.collectionProtocolEvent.studyCalendarEventPoint,"
	//			+ "scg.collectionProtocolEvent.collectionPointLabel, scg.collectionProtocolRegistration.registrationDate from "
	//			+ SpecimenCollectionGroup.class.getName() + " as scg where scg.collectionProtocolRegistration.id = :cprId"
	//			+ " and scg.activityStatus <> '" + Status.ACTIVITY_STATUS_DISABLED.toString()
	//			+ "' order by scg.collectionProtocolEvent.studyCalendarEventPoint";
}