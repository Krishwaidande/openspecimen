package com.krishagni.catissueplus.core.biospecimen.domain.factory;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import com.krishagni.catissueplus.core.biospecimen.ConfigParams;
import com.krishagni.catissueplus.core.biospecimen.domain.Participant;
import com.krishagni.catissueplus.core.biospecimen.events.PmiDetail;
import com.krishagni.catissueplus.core.biospecimen.repository.DaoFactory;
import com.krishagni.catissueplus.core.common.OpenSpecimenAppCtxProvider;
import com.krishagni.catissueplus.core.common.errors.ErrorCode;
import com.krishagni.catissueplus.core.common.errors.OpenSpecimenException;
import com.krishagni.catissueplus.core.common.util.ConfigUtil;
import com.krishagni.catissueplus.core.common.util.RegexValidator;
import com.krishagni.catissueplus.core.common.util.Validator;

public class ParticipantUtil {
	public static boolean ensureUniqueUid(DaoFactory daoFactory, String uid, OpenSpecimenException ose) {
		if (StringUtils.isBlank(uid)) {
			return true;
		}
		
		if (!daoFactory.getParticipantDao().isUidUnique(uid)) {
			ose.addError(ParticipantErrorCode.DUP_UID, uid);
			return false;
		}
		
		return true;
	}
	
	public static boolean ensureUniqueEmpi(DaoFactory daoFactory, String empi, OpenSpecimenException ose) {
		if (StringUtils.isBlank(empi)) {
			return true;
		}
		
		if (daoFactory.getParticipantDao().getByEmpi(empi) != null) {
			ose.addError(ParticipantErrorCode.DUP_EMPI, empi);
			return false;
		}
		
		return true;
	}

	public static boolean ensureUniquePmis(DaoFactory daoFactory, List<PmiDetail> pmis, Participant participant, OpenSpecimenException ose) {
		List<Long> participantIds = daoFactory.getParticipantDao().getParticipantIdsByPmis(pmis);
		if (CollectionUtils.isEmpty(participantIds)) { 
			// no one own these pmis yet
			return true;
		}
		
		if (participant.getId() == null) { // create mode
			ose.addError(ParticipantErrorCode.DUP_MRN);
			return false;
		} else {
			for (Long participantId : participantIds) {
				if (!participant.getId().equals(participantId)) {
					ose.addError(ParticipantErrorCode.DUP_MRN);
					return false;
				}
			}			
		}
		
		return true;
	}
	
	public static boolean isValidMpi(String empi, OpenSpecimenException ose) {
		return isValidInput(
				empi, 
				ConfigParams.MPI_PATTERN, 
				ConfigParams.MPI_VALIDATOR, 
				ParticipantErrorCode.INVALID_MPI, 
				ose);
	}
	
	public static boolean isValidUid(String uid, OpenSpecimenException ose) {
		return isValidInput(
				uid, 
				ConfigParams.PARTICIPANT_UID_PATTERN, 
				ConfigParams.PARTICIPANT_UID_VALIDATOR, 
				ParticipantErrorCode.INVALID_UID, 
				ose);
	}
	
	private static boolean isValidInput(String input, String patternCfg, String validatorCfg, ErrorCode error, OpenSpecimenException ose) {
		String pattern = ConfigUtil.getInstance().getStrSetting(ConfigParams.MODULE, patternCfg, null);
		
		if (StringUtils.isNotBlank(pattern)) {
			if (!RegexValidator.validate(pattern, input)) {
				ose.addError(error, input);
				return false;
			}
			
			return true;
		}
		
		String validatorName = ConfigUtil.getInstance().getStrSetting(ConfigParams.MODULE, validatorCfg, null);
		if (StringUtils.isBlank(validatorName)) {
			return true;
		}
		
		Validator validator = OpenSpecimenAppCtxProvider.getBean(validatorName);
		return validator.validate(input, ose);		
	}

	public static void ensureValidAndUniqueEmpi(DaoFactory daoFactory, String empi, OpenSpecimenException ose) {
		if(StringUtils.isNotBlank(empi) && !isValidMpi(empi, ose)){
			return;
		}
		ensureUniqueEmpi(daoFactory, empi, ose);
	}	
}
