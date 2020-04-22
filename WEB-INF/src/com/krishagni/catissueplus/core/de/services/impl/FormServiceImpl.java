package com.krishagni.catissueplus.core.de.services.impl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.web.multipart.MultipartFile;

import com.krishagni.catissueplus.core.administrative.domain.FormDataSavedEvent;
import com.krishagni.catissueplus.core.administrative.domain.User;
import com.krishagni.catissueplus.core.administrative.repository.FormListCriteria;
import com.krishagni.catissueplus.core.biospecimen.ConfigParams;
import com.krishagni.catissueplus.core.biospecimen.domain.CollectionProtocol;
import com.krishagni.catissueplus.core.biospecimen.domain.CollectionProtocolGroup;
import com.krishagni.catissueplus.core.biospecimen.domain.CollectionProtocolRegistration;
import com.krishagni.catissueplus.core.biospecimen.domain.Participant;
import com.krishagni.catissueplus.core.biospecimen.domain.Specimen;
import com.krishagni.catissueplus.core.biospecimen.domain.SpecimenSavedEvent;
import com.krishagni.catissueplus.core.biospecimen.domain.Visit;
import com.krishagni.catissueplus.core.biospecimen.domain.factory.CpGroupErrorCode;
import com.krishagni.catissueplus.core.biospecimen.domain.factory.CprErrorCode;
import com.krishagni.catissueplus.core.biospecimen.domain.factory.SpecimenErrorCode;
import com.krishagni.catissueplus.core.biospecimen.domain.factory.VisitErrorCode;
import com.krishagni.catissueplus.core.biospecimen.events.PdeTokenDetail;
import com.krishagni.catissueplus.core.biospecimen.repository.DaoFactory;
import com.krishagni.catissueplus.core.biospecimen.services.PdeTokenGenerator;
import com.krishagni.catissueplus.core.biospecimen.services.PdeTokenGeneratorRegistry;
import com.krishagni.catissueplus.core.biospecimen.services.impl.SystemFormUpdatePreventer;
import com.krishagni.catissueplus.core.common.Pair;
import com.krishagni.catissueplus.core.common.PlusTransactional;
import com.krishagni.catissueplus.core.common.TransactionalThreadLocals;
import com.krishagni.catissueplus.core.common.access.AccessCtrlMgr;
import com.krishagni.catissueplus.core.common.access.SiteCpPair;
import com.krishagni.catissueplus.core.common.errors.CommonErrorCode;
import com.krishagni.catissueplus.core.common.errors.OpenSpecimenException;
import com.krishagni.catissueplus.core.common.events.BulkDeleteEntityOp;
import com.krishagni.catissueplus.core.common.events.DependentEntityDetail;
import com.krishagni.catissueplus.core.common.events.OpenSpecimenEvent;
import com.krishagni.catissueplus.core.common.events.Operation;
import com.krishagni.catissueplus.core.common.events.RequestEvent;
import com.krishagni.catissueplus.core.common.events.Resource;
import com.krishagni.catissueplus.core.common.events.ResponseEvent;
import com.krishagni.catissueplus.core.common.service.impl.EventPublisher;
import com.krishagni.catissueplus.core.common.util.AuthUtil;
import com.krishagni.catissueplus.core.common.util.ConfigUtil;
import com.krishagni.catissueplus.core.common.util.Utility;
import com.krishagni.catissueplus.core.de.domain.DeObject;
import com.krishagni.catissueplus.core.de.domain.Form;
import com.krishagni.catissueplus.core.de.domain.FormDataEntryToken;
import com.krishagni.catissueplus.core.de.domain.FormErrorCode;
import com.krishagni.catissueplus.core.de.events.AddRecordEntryOp;
import com.krishagni.catissueplus.core.de.events.EntityFormRecords;
import com.krishagni.catissueplus.core.de.events.FileDetail;
import com.krishagni.catissueplus.core.de.events.FormContextDetail;
import com.krishagni.catissueplus.core.de.events.FormCtxtSummary;
import com.krishagni.catissueplus.core.de.events.FormDataDetail;
import com.krishagni.catissueplus.core.de.events.FormDataEntryTokenDetail;
import com.krishagni.catissueplus.core.de.events.FormFieldSummary;
import com.krishagni.catissueplus.core.de.events.FormRecordCriteria;
import com.krishagni.catissueplus.core.de.events.FormRecordSummary;
import com.krishagni.catissueplus.core.de.events.FormRecordsList;
import com.krishagni.catissueplus.core.de.events.FormSummary;
import com.krishagni.catissueplus.core.de.events.GetEntityFormRecordsOp;
import com.krishagni.catissueplus.core.de.events.GetFileDetailOp;
import com.krishagni.catissueplus.core.de.events.GetFormFieldPvsOp;
import com.krishagni.catissueplus.core.de.events.GetFormRecordsListOp;
import com.krishagni.catissueplus.core.de.events.ListEntityFormsOp;
import com.krishagni.catissueplus.core.de.events.ListFormFields;
import com.krishagni.catissueplus.core.de.events.ObjectCpDetail;
import com.krishagni.catissueplus.core.de.events.RemoveFormContextOp;
import com.krishagni.catissueplus.core.de.repository.FormDao;
import com.krishagni.catissueplus.core.de.services.FormContextProcessor;
import com.krishagni.catissueplus.core.de.services.FormService;
import com.krishagni.catissueplus.core.exporter.domain.ExportJob;
import com.krishagni.catissueplus.core.exporter.services.ExportService;
import com.krishagni.rbac.common.errors.RbacErrorCode;

import edu.common.dynamicextensions.domain.nui.Container;
import edu.common.dynamicextensions.domain.nui.Control;
import edu.common.dynamicextensions.domain.nui.DataType;
import edu.common.dynamicextensions.domain.nui.FileUploadControl;
import edu.common.dynamicextensions.domain.nui.Label;
import edu.common.dynamicextensions.domain.nui.LookupControl;
import edu.common.dynamicextensions.domain.nui.PageBreak;
import edu.common.dynamicextensions.domain.nui.PermissibleValue;
import edu.common.dynamicextensions.domain.nui.SelectControl;
import edu.common.dynamicextensions.domain.nui.SubFormControl;
import edu.common.dynamicextensions.domain.nui.UserContext;
import edu.common.dynamicextensions.napi.ControlValue;
import edu.common.dynamicextensions.napi.FileControlValue;
import edu.common.dynamicextensions.napi.FormData;
import edu.common.dynamicextensions.napi.FormDataManager;
import edu.common.dynamicextensions.napi.FormEventsNotifier;
import edu.common.dynamicextensions.nutility.FileUploadMgr;
import krishagni.catissueplus.beans.FormContextBean;
import krishagni.catissueplus.beans.FormRecordEntryBean;
import krishagni.catissueplus.beans.FormRecordEntryBean.Status;

public class FormServiceImpl implements FormService, InitializingBean {
	private static Log logger = LogFactory.getLog(FormServiceImpl.class);

	private static final String CP_FORM = "CollectionProtocol";

	private static final String PARTICIPANT_FORM = "Participant";

	private static final String COMMON_PARTICIPANT = "CommonParticipant";
	
	private static final String SCG_FORM = "SpecimenCollectionGroup";
	
	private static final String SPECIMEN_FORM = "Specimen";
	
	private static final String SPECIMEN_EVENT_FORM = "SpecimenEvent";
	
	private static Set<String> staticExtendedForms = new HashSet<>();
	
	private static Map<String, String> customFieldEntities = new HashMap<>();

	private static Map<String, Function<Long, Boolean>> entityAccessCheckers = new HashMap<>();

	private static final String PDE_FORM_TYPE = "deForms";

	static {
		staticExtendedForms.add(PARTICIPANT_FORM);
		staticExtendedForms.add(SCG_FORM);
		staticExtendedForms.add(SPECIMEN_FORM);

		customFieldEntities.put(CP_FORM, CollectionProtocol.EXTN);
		customFieldEntities.put(PARTICIPANT_FORM, Participant.EXTN);
		customFieldEntities.put(SCG_FORM, Visit.EXTN);
		customFieldEntities.put(SPECIMEN_FORM, Specimen.EXTN);
	}

	private FormDao formDao;

	private FormDataManager formDataMgr;

	private DaoFactory daoFactory;

	private com.krishagni.catissueplus.core.de.repository.DaoFactory deDaoFactory;

	private ExportService exportSvc;
	
	private Map<String, List<FormContextProcessor>> ctxtProcs = new HashMap<>();

	private PdeTokenGeneratorRegistry pdeTokenGeneratorRegistry;

	public void setFormDao(FormDao formDao) {
		this.formDao = formDao;
	}

	public void setFormDataMgr(FormDataManager formDataMgr) {
		this.formDataMgr = formDataMgr;
	}

	public void setDaoFactory(DaoFactory daoFactory) {
		this.daoFactory = daoFactory;
	}

	public void setDeDaoFactory(com.krishagni.catissueplus.core.de.repository.DaoFactory deDaoFactory) {
		this.deDaoFactory = deDaoFactory;
	}

	public void setExportSvc(ExportService exportSvc) {
		this.exportSvc = exportSvc;
	}

	public void setCtxtProcs(Map<String, List<FormContextProcessor>> ctxtProcs) {
		this.ctxtProcs = ctxtProcs;
	}

	public void setPdeTokenGeneratorRegistry(PdeTokenGeneratorRegistry pdeTokenGeneratorRegistry) {
		this.pdeTokenGeneratorRegistry = pdeTokenGeneratorRegistry;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		FormEventsNotifier.getInstance().addListener(new SystemFormUpdatePreventer(formDao));
		exportSvc.registerObjectsGenerator("extensions", this::getFormRecordsGenerator);
		pdeTokenGeneratorRegistry.register(PDE_FORM_TYPE, new PatientDataEntryTokenGenerator());
	}

	@Override
    @PlusTransactional
	public ResponseEvent<List<FormSummary>> getForms(RequestEvent<FormListCriteria> req) {
		FormListCriteria crit = req.getPayload();
		if (crit.entityTypes() != null && crit.entityTypes().size() == 1 && crit.entityTypes().contains("Query")) {
			return ResponseEvent.response(formDao.getQueryForms());
		} else {
			crit = addFormsListCriteria(crit);
			if (crit == null) {
				return ResponseEvent.response(Collections.emptyList());
			}

			List<Form> forms = formDao.getForms(crit);
			List<FormSummary> result = forms.stream().map(FormSummary::from).collect(Collectors.toList());
			if (!result.isEmpty() && crit.includeStat()) {
				Map<Long, FormSummary> formsMap = result.stream().collect(Collectors.toMap(FormSummary::getFormId, f -> f));
				Map<Long, Integer> cpCounts = formDao.getCpCounts(formsMap.keySet());
				for (FormSummary form : result) {
					form.setCpCount(cpCounts.getOrDefault(form.getFormId(), 0));
				}
			}

			return ResponseEvent.response(result);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<Long> getFormsCount(RequestEvent<FormListCriteria> req) {
		FormListCriteria crit = addFormsListCriteria(req.getPayload());
		if (crit == null) {
			return ResponseEvent.response(0L);
		}

		return ResponseEvent.response(formDao.getFormsCount(crit));
	}

    @Override
    @PlusTransactional
	public ResponseEvent<Container> getFormDefinition(RequestEvent<Long> req) {
		Container form = getContainer(req.getPayload(), null);
		return ResponseEvent.response(form);
	}
    
	@Override
	@PlusTransactional
	public ResponseEvent<Boolean> deleteForms(RequestEvent<BulkDeleteEntityOp> req) {
		try {
			AccessCtrlMgr.getInstance().ensureFormUpdateRights();

			Set<Long> formIds = req.getPayload().getIds();
			List<Form> forms = formDao.getFormsByIds(formIds);
			if (formIds.size() != forms.size()) {
				forms.forEach(form -> formIds.remove(form.getId()));
				throw OpenSpecimenException.userError(FormErrorCode.NOT_FOUND, formIds, formIds.size());
			}

			formIds.forEach(Container::softDeleteContainer);
			formDao.deleteFormContexts(formIds);

			return ResponseEvent.response(true);
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}
    
    @Override
    @PlusTransactional
	public ResponseEvent<List<FormFieldSummary>> getFormFields(RequestEvent<ListFormFields> req) {
    	ListFormFields op = req.getPayload();
		Container form = getContainer(op.getFormId(), null);
		
		List<FormFieldSummary> fields = getFormFields(form);
		if (!op.isExtendedFields()) {
			return ResponseEvent.response(fields);
		}

		Long cpId = op.getCpId();
		Long groupId = op.getCpGroupId();
		if (groupId == null || groupId <= 0) {
			fields.addAll(getCpFields(cpId, form));
		} else {
			fields.addAll(getCpGroupFields(groupId, form));
		}

		return ResponseEvent.response(fields);
	}
    
	@Override
	@PlusTransactional
	public ResponseEvent<List<FormContextDetail>> getFormContexts(RequestEvent<Long> req) {
		return ResponseEvent.response(formDao.getFormContexts(req.getPayload()));
	}
	
	@Override
	@PlusTransactional
	public ResponseEvent<List<FormContextDetail>> addFormContexts(RequestEvent<List<FormContextDetail>> req) { // TODO: check form is deleted
		try {
			// AccessCtrlMgr.getInstance().ensureFormUpdateRights();
			List<FormContextDetail> formCtxts = req.getPayload();
			for (FormContextDetail formCtxtDetail : formCtxts) {
				Long formId = formCtxtDetail.getFormId();
				Long cpId = formCtxtDetail.getCollectionProtocol().getId();
				String entity = formCtxtDetail.getLevel();
				Long entityId = formCtxtDetail.getEntityId();

				if (entityId != null) {
					Function<Long, Boolean> accessChecker = entityAccessCheckers.get(entity);
					if (accessChecker != null && !accessChecker.apply(entityId)) {
						throw OpenSpecimenException.userError(RbacErrorCode.ACCESS_DENIED);
					}
				} else if (cpId == -1L && !AuthUtil.isAdmin()) {
					throw OpenSpecimenException.userError(RbacErrorCode.ACCESS_DENIED);
				} else if (cpId != -1L) {
					AccessCtrlMgr.getInstance().ensureUpdateCpRights(cpId);
				}
				

				FormContextBean formCtxt = formDao.getFormContext(entityId == null, entity, entityId == null ? cpId : entityId, formId);
				if (formCtxt == null) {
					formCtxt = new FormContextBean();
					formCtxt.setContainerId(formId);
					formCtxt.setCpId(entity.equals(SPECIMEN_EVENT_FORM) ? -1 : cpId);
					formCtxt.setEntityType(entity);
					formCtxt.setEntityId(entityId);
				}

				formCtxt.setMultiRecord(formCtxtDetail.isMultiRecord());
				formCtxt.setSortOrder(formCtxtDetail.getSortOrder());

				notifyContextSaved(formCtxt);
				formDao.saveOrUpdate(formCtxt);
				formCtxtDetail.setFormCtxtId(formCtxt.getIdentifier());
			}
			
			return ResponseEvent.response(formCtxts);
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<List<FormCtxtSummary>> getEntityForms(RequestEvent<ListEntityFormsOp> req) {
		try {
			ListEntityFormsOp opDetail = req.getPayload();
			
			List<FormCtxtSummary> forms = null;
			
			Long entityId = opDetail.getEntityId();
			switch (opDetail.getEntityType()) {
			    case COLLECTION_PROTOCOL_REGISTRATION:
			    	AccessCtrlMgr.getInstance().ensureReadCprRights(entityId);
					forms = formDao.getParticipantForms(entityId);
					forms.addAll(formDao.getCprForms(entityId));
			    	break;
			    	
			    case SPECIMEN:
			    	AccessCtrlMgr.getInstance().ensureReadSpecimenRights(entityId);
			    	forms = formDao.getSpecimenForms(opDetail.getEntityId());
			    	break;
			    	
			    case SPECIMEN_COLLECTION_GROUP:
			    	AccessCtrlMgr.getInstance().ensureReadVisitRights(entityId);
			    	forms = formDao.getScgForms(opDetail.getEntityId());
			    	break;

			    case SPECIMEN_EVENT :
			    	AccessCtrlMgr.getInstance().ensureReadSpecimenRights(entityId);
			    	forms = formDao.getSpecimenEventForms(opDetail.getEntityId());
			    	break;
			}
			
			return ResponseEvent.response(forms);			
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<EntityFormRecords> getEntityFormRecords(RequestEvent<GetEntityFormRecordsOp> req) {
		GetEntityFormRecordsOp opDetail = req.getPayload();
		
		FormSummary form = formDao.getFormByContext(opDetail.getFormCtxtId());
		List<FormRecordSummary> formRecs = formDao.getFormRecords(opDetail.getFormCtxtId(), opDetail.getEntityId());
		
		EntityFormRecords result = new EntityFormRecords();
		result.setFormId(form.getFormId());
		result.setFormCaption(form.getCaption());
		result.setFormCtxtId(opDetail.getFormCtxtId());
		result.setRecords(formRecs);
		
		return ResponseEvent.response(result);
	}
	
	@Override
	@PlusTransactional
	public ResponseEvent<FormDataDetail> getFormData(RequestEvent<FormRecordCriteria> req) {
		try {
			FormRecordCriteria crit = req.getPayload();
			Container form = getContainer(crit.getFormId(), null);
			FormData record = getRecord(form, crit.getRecordId());
			return ResponseEvent.response(FormDataDetail.ok(crit.getFormId(), crit.getRecordId(), record));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<List<FormDataDetail>> getLatestRecords(RequestEvent<FormRecordCriteria> req) {
		try {
			FormRecordCriteria crit = req.getPayload();
			Container form = getContainer(crit.getFormId(), null);
			Map<Long, Pair<Long, Long>> recordIds = formDao.getLatestRecordIds(
				crit.getFormId(), crit.getEntityType(), crit.getObjectIds());

			List<FormDataDetail> records = new ArrayList<>();
			recordIds.forEach((objectId, recordId) -> {
				FormData record = getRecord(form, objectId, recordId.first(), crit.getEntityType(), recordId.second());
				records.add(FormDataDetail.ok(form.getId(), record.getRecordId(), record));
			});

			return ResponseEvent.response(records);
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<FormDataDetail> saveFormData(RequestEvent<FormDataDetail> req) {
		FormDataDetail detail = req.getPayload();
		
		try {
			FormData formData = saveOrUpdateFormData(detail.getRecordId(), detail.getFormData(), detail.isPartial());
			return ResponseEvent.response(FormDataDetail.ok(formData.getContainer().getId(), formData.getRecordId(), formData));
		} catch(IllegalArgumentException ex) {
			return ResponseEvent.userError(FormErrorCode.INVALID_DATA, ex.getMessage());
		} catch (DataAccessException dae) {
			return ResponseEvent.userError(CommonErrorCode.SQL_EXCEPTION, dae.getMessage());
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<List<FormData>> saveBulkFormData(RequestEvent<List<FormData>> req) {
		try{
			List<FormData> formDataList = req.getPayload();
			List<FormData> savedFormDataList = new ArrayList<>();
			for (FormData formData : formDataList) {
				FormData savedFormData = saveOrUpdateFormData(formData.getRecordId(), formData, true);
				savedFormDataList.add(savedFormData);
			}
			
			return ResponseEvent.response(savedFormDataList);
		} catch(IllegalArgumentException ex) {
			return ResponseEvent.userError(FormErrorCode.INVALID_DATA, ex.getMessage());
		} catch (DataAccessException dae) {
			return ResponseEvent.userError(CommonErrorCode.SQL_EXCEPTION, dae.getMessage());
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}	
	}

	@Override
	@PlusTransactional
	public ResponseEvent<FileDetail> getFileDetail(RequestEvent<GetFileDetailOp> req) {
		GetFileDetailOp op = req.getPayload();

		FileControlValue fcv = null;
		if (op.getRecordId() != null) {
			fcv = formDataMgr.getFileControlValue(op.getFormId(), op.getRecordId(), op.getCtrlName());
		} else if (StringUtils.isNotBlank(op.getFileId())) {
			fcv = formDataMgr.getFileControlValue(op.getFormId(), op.getCtrlName(), op.getFileId());
		}

		if (fcv == null) {
			return ResponseEvent.userError(FormErrorCode.FILE_NOT_FOUND);
		}
		
		return ResponseEvent.response(FileDetail.from(fcv));
	}
	
	@Override
	public ResponseEvent<FileDetail> uploadFile(RequestEvent<MultipartFile> req) {
		MultipartFile input = req.getPayload();
		
		FileDetail fileDetail = new FileDetail();
		fileDetail.setFilename(input.getOriginalFilename());
		fileDetail.setSize(input.getSize());
		fileDetail.setContentType(input.getContentType());
		
		try {
			InputStream in = input.getInputStream();
			String fileId = FileUploadMgr.getInstance().saveFile(in);
			fileDetail.setFileId(fileId);
			return ResponseEvent.response(fileDetail);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}		
	}

	@Override
	public ResponseEvent<FileDetail> uploadImage(RequestEvent<String> req) {
		String dataUrl = req.getPayload();
		if (StringUtils.isBlank(dataUrl)) {
			return ResponseEvent.response(null);
		}

		String[] parts = dataUrl.split(",", 2);
		if (parts.length == 1) {
			throw OpenSpecimenException.userError(CommonErrorCode.INVALID_INPUT, "Invalid image data URL");
		}

		String header = parts[0];
		String base64Img = parts[1];

		String[] headerParts = header.split("[:/;]");
		if (headerParts.length < 4) {
			throw OpenSpecimenException.userError(CommonErrorCode.INVALID_INPUT, "Invalid image data URL");
		}

		String type = headerParts[2];
		byte[] imgBinary = Base64.getDecoder().decode(base64Img);
		ByteArrayInputStream bin = new ByteArrayInputStream(imgBinary);

		try {
			String fileId = FileUploadMgr.getInstance().saveFile(bin, type);

			FileDetail result = new FileDetail();
			result.setFileId(fileId);
			result.setContentType("image/" + type);
			result.setSize(imgBinary.length);
			return ResponseEvent.response(result);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}
	
	@Override
	@PlusTransactional
	public ResponseEvent<Long> deleteRecord(RequestEvent<FormRecordCriteria> req) {
		try {
			FormRecordCriteria crit = req.getPayload();
			FormRecordEntryBean recEntry = formDao.getRecordEntry(crit.getFormId(), crit.getRecordId());
			if (recEntry == null) {
				return ResponseEvent.userError(FormErrorCode.REC_NOT_FOUND);
			}

			if (recEntry.getFormCtxt().isSysForm()) {
				return ResponseEvent.userError(FormErrorCode.SYS_REC_DEL_NOT_ALLOWED);
			}
			
			String entityType = recEntry.getEntityType();
			Long objectId = recEntry.getObjectId();
			if (entityType.equals("Participant")) {
				AccessCtrlMgr.getInstance().ensureUpdateCprRights(objectId);
			} else if (entityType.equals("SpecimenCollectionGroup")) {
				AccessCtrlMgr.getInstance().ensureCreateOrUpdateVisitRights(objectId, false);
			} else if (entityType.equals("Specimen") || entityType.equals("SpecimenEvent")) {
				ensureSpecimenUpdateRights(objectId, false);
			}
			
			recEntry.delete();
			formDao.saveOrUpdateRecordEntry(recEntry);
			return  ResponseEvent.response(crit.getRecordId());
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}
	
	@Override
	@PlusTransactional
	public ResponseEvent<Boolean> removeFormContext(RequestEvent<RemoveFormContextOp> req) {
		try {
			AccessCtrlMgr.getInstance().ensureFormUpdateRights();

			RemoveFormContextOp opDetail = req.getPayload();
			Long cpId = opDetail.getCpId();
			Long entityId = opDetail.getEntityId();
			FormContextBean formCtx = formDao.getFormContext(
				entityId == null,
				opDetail.getEntityType(),
				entityId == null ? opDetail.getCpId() : entityId,
				opDetail.getFormId());

			if (formCtx == null) {
				return ResponseEvent.userError(FormErrorCode.NO_ASSOCIATION, cpId, opDetail.getFormId()	);
			}
			
			if (formCtx.isSysForm()) {
				return ResponseEvent.userError(FormErrorCode.SYS_FORM_DEL_NOT_ALLOWED);
			}

			if (entityId != null) {
				Function<Long, Boolean> accessChecker = entityAccessCheckers.get(opDetail.getEntityType());
				if (accessChecker != null && !accessChecker.apply(entityId)) {
					return ResponseEvent.userError(RbacErrorCode.ACCESS_DENIED);
				}
			} else if (cpId == -1L && !AuthUtil.isAdmin()) {
				return ResponseEvent.userError(RbacErrorCode.ACCESS_DENIED);
			} else if (cpId != -1L) {
				AccessCtrlMgr.getInstance().ensureUpdateCpRights(cpId);
			}

			notifyContextRemoved(formCtx);
			switch (opDetail.getRemoveType()) {
				case SOFT_REMOVE:
					formCtx.setDeletedOn(Calendar.getInstance().getTime());
					break;
					
				case HARD_REMOVE:
					formDao.delete(formCtx);
					break;
			}
			
			return ResponseEvent.response(true);
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}
		
	@Override
	@PlusTransactional
	public ResponseEvent<Long> addRecordEntry(RequestEvent<AddRecordEntryOp> req) {
		AddRecordEntryOp opDetail = req.getPayload();
		String entityType = (String) opDetail.getRecIntegrationInfo().get("entityType");

		ObjectCpDetail objCp = formDao.getObjectCpDetail(opDetail.getRecIntegrationInfo());
		Long formCtxtId = formDao.getFormCtxtId(opDetail.getContainerId(), entityType, objCp.getCpId());

		FormRecordEntryBean recordEntry = new FormRecordEntryBean();
		recordEntry.setFormCtxtId(formCtxtId);
		recordEntry.setObjectId(objCp.getObjectId());
		recordEntry.setRecordId(opDetail.getRecordId());
		recordEntry.setUpdatedBy(AuthUtil.getCurrentUser().getId());
		recordEntry.setUpdatedTime(Calendar.getInstance().getTime());
		recordEntry.setActivityStatus(Status.ACTIVE);

		formDao.saveOrUpdateRecordEntry(recordEntry);		
		return ResponseEvent.response(recordEntry.getIdentifier());
	}
		
	@Override
	@PlusTransactional
	public ResponseEvent<List<FormRecordsList>> getFormRecords(RequestEvent<GetFormRecordsListOp> req) {
		try {
			GetFormRecordsListOp input = req.getPayload();
			String entityType = input.getEntityType();
			Long objectId = input.getObjectId();

			if (entityType.equals("Participant")) {
				AccessCtrlMgr.getInstance().ensureReadCprRights(objectId);
			} else if (entityType.equals("SpecimenCollectionGroup")) {
				AccessCtrlMgr.getInstance().ensureReadVisitRights(objectId);
			} else if (entityType.equals("Specimen") || entityType.equals("SpecimenEvent")) {
				AccessCtrlMgr.getInstance().ensureReadSpecimenRights(objectId);
			}

			List<FormRecordsList> result = getFormRecords(entityType, input.getFormId(), objectId);
			if (entityType.equals("Participant")) {
				objectId = daoFactory.getCprDao().getById(objectId).getParticipant().getId();
				result.addAll(0, getFormRecords("CommonParticipant", input.getFormId(), objectId));
			}

			return ResponseEvent.response(result);			
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}
	
	@PlusTransactional
	public ResponseEvent<List<DependentEntityDetail>> getDependentEntities(RequestEvent<Long> req) {
		try {
			return ResponseEvent.response(formDao.getDependentEntities(req.getPayload()));
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<FormDataEntryTokenDetail> createDataEntryToken(RequestEvent<FormDataEntryTokenDetail> req) {
		try {
			FormDataEntryTokenDetail input = req.getPayload();
			FormDataEntryToken token = createDataEntryToken(input.getFormCtxtId(), input.getObjectId());
			return ResponseEvent.response(FormDataEntryTokenDetail.from(token));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	@PlusTransactional
	public ResponseEvent<FormDataEntryTokenDetail> getDataEntryToken(RequestEvent<String> req) {
		try {
			String token = req.getPayload();
			FormDataEntryToken fdeToken = deDaoFactory.getFormDataEntryTokenDao().getByToken(token);
			if (fdeToken == null || !fdeToken.isValid()) {
				return ResponseEvent.userError(FormErrorCode.INVALID_TOKEN, token);
			}

			FormContextBean fctxt = fdeToken.getFormCtxt();
			FormDataEntryTokenDetail tokenDetail = FormDataEntryTokenDetail.from(fdeToken);
			if ("Participant".equals(fctxt.getEntityType()) || "CommonParticipant".equals(fctxt.getEntityType())) {
				CollectionProtocolRegistration cpr = daoFactory.getCprDao().getById(fdeToken.getObjectId());
				if (cpr == null) {
					return ResponseEvent.userError(CprErrorCode.NOT_FOUND, fdeToken.getObjectId());
				}

				tokenDetail.setCpId(cpr.getCollectionProtocol().getId());
				tokenDetail.setCpShortTitle(cpr.getCpShortTitle());
				tokenDetail.setRecordLabel(cpr.getPpid());
			} else {
				return ResponseEvent.userError(FormErrorCode.OP_NOT_ALLOWED);
			}

			return ResponseEvent.response(tokenDetail);
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	//
	// Internal APIs
	//
	@Override
	public List<FormData> getSummaryRecords(Long formId, List<Long> recordIds) {
		return formDataMgr.getSummaryData(formId, recordIds);
	}

	@Override
	public FormData getRecord(Container form, Long recordId) {
		return getRecord(form, null, null, null, recordId);
	}

	@Override
	public List<FormData> getRecords(Container form, List<Long> recordIds) {
		return formDataMgr.getFormData(form, recordIds);
	}

	@Override
	public ResponseEvent<List<PermissibleValue>> getPvs(RequestEvent<GetFormFieldPvsOp> req) {
		try {
			GetFormFieldPvsOp input = req.getPayload();
			Container form = getContainer(input.getFormId(), input.getFormName());

			String controlName = input.getControlName();
			Control control;
			if (input.isUseUdn()) {
				control = form.getControlByUdn(controlName, "\\.");
			} else {
				control = form.getControl(controlName, "\\.");
			}

			if (!(control instanceof SelectControl)) {
				return ResponseEvent.userError(FormErrorCode.NOT_SELECT_CONTROL, controlName);
			}

			String searchStr = input.getSearchString();
			int maxResults = input.getMaxResults() <= 0 ? 100 : input.getMaxResults();

			SelectControl selectControl = (SelectControl) control;
			List<PermissibleValue> pvs = selectControl.getPvDataSource().getPermissibleValues(searchStr, maxResults);
			return ResponseEvent.response(pvs);
		} catch (IllegalArgumentException iae) {
			return ResponseEvent.error(OpenSpecimenException.userError(CommonErrorCode.INVALID_INPUT, iae.getMessage()));
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}

	@Override
	public void addFormContextProc(String entity, FormContextProcessor proc) {
		List<FormContextProcessor> procs = ctxtProcs.get(entity);
		if (procs == null) {
			procs = new ArrayList<>();
			ctxtProcs.put(entity, procs);
		}

		boolean exists = false;
		for (FormContextProcessor existing : procs) {
			if (existing == proc) {
				exists = true;
				break;
			}
		}

		if (!exists) {
			procs.add(proc);
		}
	}

	@Override
	@PlusTransactional
	public Map<String, Object> getExtensionInfo(Long cpId, String entityType) {
		return getExtensionInfo(true, entityType, cpId);
	}

	@Override
	@PlusTransactional
	public Map<String, Object> getExtensionInfo(boolean cpBased, String entityType, Long entityId) {
		return DeObject.getFormInfo(cpBased, entityType, entityId);
	}

	@Override
	@PlusTransactional
	public List<FormSummary> getEntityForms(Long cpId, String[] entityTypes) {
		FormListCriteria crit = new FormListCriteria()
			.cpIds(Arrays.asList(-1L, cpId))
			.entityTypes(Arrays.asList(entityTypes));
		return formDao.getEntityForms(crit);
	}

	//
	// anonymize. Used by internal code
	//
	@Override
	@PlusTransactional
	public void anonymizeRecord(Container form, Long recordId) {
		FormRecordEntryBean recEntry = formDao.getRecordEntry(form.getId(), recordId);
		if (recEntry == null) {
			throw OpenSpecimenException.userError(FormErrorCode.REC_NOT_FOUND);
		}

		formDataMgr.anonymize(
			new UserContext() {
				@Override
				public Long getUserId() {
					return AuthUtil.getCurrentUser().getId();
				}

				@Override
				public String getUserName() {
					return AuthUtil.getCurrentUser().getLoginName();
				}

				@Override
				public String getIpAddress() {
					return AuthUtil.getRemoteAddr();
				}
			},
			form,
			recordId
		);

		recEntry.setUpdatedBy(AuthUtil.getCurrentUser().getId());
		recEntry.setUpdatedTime(Calendar.getInstance().getTime());
		formDao.saveOrUpdateRecordEntry(recEntry);
	}

	private FormListCriteria addFormsListCriteria(FormListCriteria crit) {
		User currUser = AuthUtil.getCurrentUser();
		if (!currUser.isAdmin() && !currUser.getManageForms()) {
			return null;
		} else if (!currUser.isAdmin() && currUser.getManageForms()) {
			crit.userId(currUser.getId());
			crit.siteCps(AccessCtrlMgr.getInstance().getReadableSiteCps());
		}
		
		return crit;
	}

	private List<FormFieldSummary> getCpFields(Long cpId, Container form) {
		List<FormFieldSummary> fields = new ArrayList<>();
		if (cpId == null || cpId < 0) {
			cpId = -1L;
		}

		String formName = form.getName();
		String entityType = customFieldEntities.get(formName);
		if (StringUtils.isNotBlank(entityType)) {
			Map<String, Object> extnInfo = getExtensionInfo(cpId, entityType);
			if (extnInfo == null && cpId != -1L) {
				extnInfo = getExtensionInfo(-1L, entityType);
			}

			if (extnInfo != null) {
				Long extnFormId = (Long)extnInfo.get("formId");
				fields.add(getExtensionField("customFields", "Custom Fields", Collections.singletonList(extnFormId)));
			}
		}

		if (!staticExtendedForms.contains(formName)) {
			return fields;
		}

		List<Long> extendedFormIds = formDao.getFormIds(cpId, formName);
		if (formName.equals(PARTICIPANT_FORM)) {
			extendedFormIds.addAll(0, formDao.getFormIds(-1L, COMMON_PARTICIPANT));
		} else if (formName.equals(SPECIMEN_FORM)) {
			extendedFormIds.addAll(formDao.getFormIds(-1L, SPECIMEN_EVENT_FORM));
		}

		fields.add(getExtensionField("extensions", "Extensions", extendedFormIds));
		return fields;
	}

	private List<FormFieldSummary> getCpGroupFields(Long groupId, Container form) {
		if (groupId == null || groupId <= 0) {
			return Collections.emptyList();
		}

		CollectionProtocolGroup group = null;
		if (customFieldEntities.containsKey(form.getName()) || staticExtendedForms.contains(form.getName())) {
			group = daoFactory.getCpGroupDao().getById(groupId);
			if (group == null) {
				throw OpenSpecimenException.userError(CpGroupErrorCode.NOT_FOUND, groupId);
			}
		}

		if (group == null) {
			return Collections.emptyList();
		}

		List<FormFieldSummary> fields = new ArrayList<>();

		String entityType = customFieldEntities.get(form.getName());
		if (StringUtils.isNotBlank(entityType)) {
			List<Long> formIds = group.getForms(entityType).stream()
				.map(f -> f.getForm().getId())
				.collect(Collectors.toList());
			fields.add(getExtensionField("customFields", "Custom Fields", formIds));
		}

		if (!staticExtendedForms.contains(form.getName())) {
			return fields;
		}

		List<Long> extendedFormIds = group.getForms(form.getName()).stream()
			.map(f -> f.getForm().getId())
			.collect(Collectors.toList());

		if (form.getName().equals(PARTICIPANT_FORM)) {
			extendedFormIds.addAll(0, formDao.getFormIds(-1L, COMMON_PARTICIPANT));
		} else if (form.getName().equals(SPECIMEN_FORM)) {
			extendedFormIds.addAll(formDao.getFormIds(-1L, SPECIMEN_EVENT_FORM));
		}

		fields.add(getExtensionField("extensions", "Extensions", extendedFormIds));
		return fields;
	}

	private FormFieldSummary getExtensionField(String name, String caption, List<Long> extendedFormIds ) {
		FormFieldSummary field = new FormFieldSummary();
		field.setName(name);
		field.setCaption(caption);
		field.setType("SUBFORM");

		List<FormFieldSummary> extensionFields = new ArrayList<FormFieldSummary>();
		for (Long extendedFormId : extendedFormIds) {
			Container form = Container.getContainer(extendedFormId);

			FormFieldSummary extensionField = new FormFieldSummary();
			extensionField.setName(form.getName());
			extensionField.setCaption(form.getCaption());
			extensionField.setType("SUBFORM");
			extensionField.setSubFields(getFormFields(form));
			extensionField.getSubFields().add(0, getRecordIdField(form));
			extensionFields.add(extensionField);
		}

		field.setSubFields(extensionFields);
		return field;
	}

	private FormData saveOrUpdateFormData(Long recordId, FormData formData, boolean isPartial) {
		Map<String, Object> appData = formData.getAppData();

		String token = (String) appData.get("fdeToken");
		FormDataEntryToken fdeToken = null;
		if (StringUtils.isNotBlank(token)) {
			fdeToken = deDaoFactory.getFormDataEntryTokenDao().getByToken(token);
			if (fdeToken == null) {
				throw OpenSpecimenException.userError(FormErrorCode.INVALID_TOKEN, token);
			}

			appData.put("formCtxtId", fdeToken.getFormCtxt().getIdentifier());
			appData.put("objectId", fdeToken.getObjectId());
		}

		if (appData.get("formCtxtId") == null || appData.get("objectId") == null) {
			throw new IllegalArgumentException("Invalid form context id or object id ");
		}

		Long objectId = ((Number) appData.get("objectId")).longValue();
		List<Long> formCtxtId = new ArrayList<>();
		formCtxtId.add(((Number) appData.get("formCtxtId")).longValue());
		
		List<FormContextBean> formContexts = formDao.getFormContextsById(formCtxtId);
		if (CollectionUtils.isEmpty(formContexts)) {
			throw new IllegalArgumentException("Invalid form context id");
		}

		FormContextBean formContext = formContexts.get(0);
		Container form = formData.getContainer();
		if (formContext.isSysForm() && !isCollectionOrReceivedEvent(form)) {
			throw OpenSpecimenException.userError(FormErrorCode.SYS_REC_EDIT_NOT_ALLOWED);
		}

		boolean isInsert = (recordId == null);
		if (!isInsert && isPartial) {
			FormData existing = formDataMgr.getFormData(formData.getContainer(), formData.getRecordId());
			formData = updateFormData(existing, formData);
		}
		
		formData.validate();

		OpenSpecimenEvent<?> collOrRecvEvent = null;
		Object object = null;

		String entityType = formContext.getEntityType();
		if (entityType.equals("Participant")) {
			CollectionProtocolRegistration cpr = daoFactory.getCprDao().getById(objectId);
			if (cpr == null) {
				throw OpenSpecimenException.userError(CprErrorCode.NOT_FOUND, objectId);
			}

			AccessCtrlMgr.getInstance().ensureUpdateCprRights(cpr);
			object = cpr;
		} else if (entityType.equals("CommonParticipant")) {
			CollectionProtocolRegistration cpr = daoFactory.getCprDao().getById(objectId);
			if (cpr == null) {
				throw OpenSpecimenException.userError(CprErrorCode.NOT_FOUND, objectId);
			}

			AccessCtrlMgr.getInstance().ensureUpdateCprRights(cpr);
			objectId = cpr.getParticipant().getId();
			object = cpr;
		} else if (entityType.equals("SpecimenCollectionGroup")) {
			Visit visit = daoFactory.getVisitsDao().getById(objectId);
			if (visit == null) {
				throw OpenSpecimenException.userError(VisitErrorCode.NOT_FOUND, objectId);
			}

			AccessCtrlMgr.getInstance().ensureCreateOrUpdateVisitRights(visit, form.hasPhiFields());
			object = visit;
		} else if (entityType.equals("Specimen") || entityType.equals("SpecimenEvent")) {
			Specimen specimen = ensureSpecimenUpdateRights(objectId, form.hasPhiFields());
			object = specimen;
			if (isCollectionOrReceivedEvent(form)) {
				specimen.setUpdated(true);
				collOrRecvEvent = new SpecimenSavedEvent(specimen);
			}
		}

		formData.setRecordId(recordId);
		
		FormRecordEntryBean recordEntry = null;
		
		if (isInsert) {
			if (!formContext.isMultiRecord()) {
				Long noOfRecords = formDao.getRecordsCount(formContext.getIdentifier(), objectId);
				if (noOfRecords >= 1L) {
					throw OpenSpecimenException.userError(FormErrorCode.MULTIPLE_RECS_NOT_ALLOWED);
				}
			}
			
			recordEntry = new FormRecordEntryBean();
			recordEntry.setActivityStatus(Status.ACTIVE);			
		} else {
			recordEntry = formDao.getRecordEntry(formContext.getIdentifier(), objectId, recordId);
			if (recordEntry == null || recordEntry.getActivityStatus() != Status.ACTIVE) {
				throw OpenSpecimenException.userError(FormErrorCode.INVALID_REC_ID, recordId);				
			}
		}

		recordId = formDataMgr.saveOrUpdateFormData(getUserContext(), formData);

		recordEntry.setFormCtxtId(formContext.getIdentifier());
		recordEntry.setObjectId(objectId);
		recordEntry.setRecordId(recordId);
		recordEntry.setUpdatedBy(AuthUtil.getCurrentUser().getId());
		recordEntry.setUpdatedTime(Calendar.getInstance().getTime());
		formDao.saveOrUpdateRecordEntry(recordEntry);
		formData.setRecordId(recordId);

		if (fdeToken != null) {
			fdeToken.setCompletionTime(Calendar.getInstance().getTime());
			fdeToken.setStatus(FormDataEntryToken.Status.COMPLETED);
			daoFactory.getPdeNotifDao().updateLinkStatus(PDE_FORM_TYPE, fdeToken.getId(), FormDataEntryToken.Status.COMPLETED.name());
		}

		if (collOrRecvEvent != null) {
			EventPublisher.getInstance().publish(collOrRecvEvent);
		} else if (object != null) {
			EventPublisher.getInstance().publish(new FormDataSavedEvent(entityType, object, formData));
		}

		return formData;
	}

	private boolean isCollectionOrReceivedEvent(Container form) {
		return isCollectionOrReceivedEvent(form.getName());
	}

	private boolean isCollectionOrReceivedEvent(String name) {
		return name.equals("SpecimenCollectionEvent") || name.equals("SpecimenReceivedEvent");
	}

	private UserContext getUserContext() {
		return new UserContext() {
			@Override
			public Long getUserId() {
				return AuthUtil.getCurrentUser() != null ? AuthUtil.getCurrentUser().getId() : null;
			}

			@Override
			public String getUserName() {
				return AuthUtil.getCurrentUser() != null ? AuthUtil.getCurrentUser().getLoginName() : null;
			}

			@Override
			public String getIpAddress() {
				return AuthUtil.getRemoteAddr();
			}
		};
	}
	
	private List<FormFieldSummary> getFormFields(Container container) {
        List<FormFieldSummary> fields = new ArrayList<>();

        for (Control control : container.getControls()) {
        	if (control.isHidden()) {
        		continue;
        	}

            FormFieldSummary field = new FormFieldSummary();
            field.setName(control.getUserDefinedName());
            field.setCaption(control.getCaption());

            if (control instanceof SubFormControl) {
            	SubFormControl sfCtrl = (SubFormControl)control;
            	field.setFlatten(sfCtrl.isFlatten());

            	if (!sfCtrl.isPathLink()) {
                	field.setType("SUBFORM");
                	field.setSubFields(getFormFields(sfCtrl.getSubContainer()));
                	fields.add(field);
            	} else if (sfCtrl.getName().equals("customFields") && StringUtils.isNotBlank(sfCtrl.getCustomFieldsInfo())) {
            		String[] info = sfCtrl.getCustomFieldsInfo().split(":");  // cpBased:entityType:entityId => false:OrderExtension:-1
					Map<String, Object> extnInfo = getExtensionInfo(Boolean.parseBoolean(info[0]), info[1], Long.parseLong(info[2]));
					if (extnInfo != null) {
						Long extnFormId = (Long)extnInfo.get("formId");
						fields.add(getExtensionField("customFields", "Custom Fields", Arrays.asList(extnFormId)));
					}
				}
            } else if (!(control instanceof Label || control instanceof PageBreak)) {
            	DataType dataType = getType(control);
            	field.setType(dataType.name());
            	                
            	if (control instanceof SelectControl) {
            		SelectControl selectCtrl = (SelectControl)control;
					List<String> pvs = selectCtrl.getPvDataSource()
						.getPermissibleValues(Calendar.getInstance().getTime(), 100)
						.stream().map(PermissibleValue::getValue)
						.collect(Collectors.toList());
            		field.setPvs(pvs);
            	} else if (control instanceof LookupControl) {
            		LookupControl luCtrl = (LookupControl)control;
            		field.setLookupProps(luCtrl.getPvSourceProps());
            	}
            	
            	fields.add(field);
            }
        }

        return fields;		
	}

	private FormData getRecord(Container form, Long objectId, Long formCtxtId, String entityType, Long recordId) {
		FormData formData = formDataMgr.getFormData(form, recordId);
		if (formData == null) {
			throw OpenSpecimenException.userError(FormErrorCode.REC_NOT_FOUND);
		}

		FormRecordEntryBean record = null;
		if (objectId == null || entityType == null) {
			record     = formDao.getRecordEntry(formData.getContainer().getId(), formData.getRecordId());
			objectId   = record.getObjectId();
			formCtxtId = record.getFormCtxtId();
			entityType = record.getEntityType();
		}

		if (formData.getContainer().hasPhiFields() && !isPhiAccessAllowed(entityType, objectId)) {
			formData.maskPhiFieldValues();
		}

		Map<String, Object> appData = formData.getAppData();
		appData.put("formCtxtId", formCtxtId);
		appData.put("objectId",   objectId);
		if (record != null) {
			appData.put("sysForm",     record.isSysRecord());
			appData.put("multiRecord", record.isMultiRecord());
		}

		return formData;
	}

	private boolean isPhiAccessAllowed(String entityType, Long objectId) {
		boolean allowPhiAccess = false;
		if (entityType.equals(PARTICIPANT_FORM) || Participant.EXTN.equals(entityType)) {
			allowPhiAccess = AccessCtrlMgr.getInstance().ensureReadCprRights(objectId);
		} else if (entityType.equals(COMMON_PARTICIPANT)) {
			allowPhiAccess = AccessCtrlMgr.getInstance().ensureReadParticipantRights(objectId);
		} else if (entityType.equals(SCG_FORM) || Visit.EXTN.equals(entityType)) {
			allowPhiAccess = AccessCtrlMgr.getInstance().ensureReadVisitRights(objectId, true);
		} else if (entityType.equals(SPECIMEN_FORM) || entityType.equals(SPECIMEN_EVENT_FORM) || Specimen.EXTN.equals(entityType)) {
			allowPhiAccess = AccessCtrlMgr.getInstance().ensureReadSpecimenRights(objectId, true);
		}

		return allowPhiAccess;
	}

	private FormFieldSummary getRecordIdField(Container form) {
		Control pkCtrl = form.getPrimaryKeyControl();

		FormFieldSummary field = new FormFieldSummary();
		field.setName(pkCtrl.getUserDefinedName());
		field.setCaption(pkCtrl.getCaption());
		field.setType(getType(pkCtrl).name());
		return field;
	}
	
	private DataType getType(Control ctrl) {
		if (ctrl instanceof FileUploadControl) {
			return DataType.STRING;
		} else if (ctrl instanceof LookupControl) {
			return ((LookupControl)ctrl).getValueType();
		} else {
			return ctrl.getDataType();
		}
	}
	
	private FormData updateFormData(FormData existing, FormData formData) {
		existing.setAppData(formData.getAppData());
		for (ControlValue ctrlValue : formData.getFieldValues()) {
			existing.addFieldValue(ctrlValue);
		}
		
		return existing;
	}

	private void notifyContextSaved(FormContextBean formCtxt) {
		notifyContextSaved(formCtxt.getEntityType(), formCtxt);
		notifyContextSaved("*", formCtxt);
	}

	private void notifyContextSaved(String entityType, FormContextBean formCtxt) {
		List<FormContextProcessor> procs = ctxtProcs.get(entityType);
		if (procs != null) {
			procs.forEach(proc -> proc.onSaveOrUpdate(formCtxt));
		}
	}

	private void notifyContextRemoved(FormContextBean formCtxt) {
		notifyContextRemoved(formCtxt.getEntityType(), formCtxt);
		notifyContextRemoved("*", formCtxt);
	}

	private void notifyContextRemoved(String entityType, FormContextBean formCtxt) {
		List<FormContextProcessor> procs = ctxtProcs.get(entityType);
		if (procs != null) {
			procs.forEach(proc -> proc.onRemove(formCtxt));
		}
	}

	private List<FormRecordsList> getFormRecords(String entityType, Long inputFormId, Long objectId) {
		List<FormRecordsList> result = new ArrayList<>();

		Map<Long, List<FormRecordSummary>> records = formDao.getFormRecords(objectId, entityType, inputFormId);
		for (Map.Entry<Long, List<FormRecordSummary>> formRecs : records.entrySet()) {
			Long formId = formRecs.getKey();
			Container container = Container.getContainer(formId);

			List<Long> recIds = new ArrayList<>();
			Map<Long, FormRecordSummary> recMap = new HashMap<>();
			for (FormRecordSummary rec : formRecs.getValue()) {
				recMap.put(rec.getRecordId(), rec);
				recIds.add(rec.getRecordId());
			}

			List<FormData> summaryRecs = formDataMgr.getSummaryData(container, recIds);
			for (FormData rec : summaryRecs) {
				recMap.get(rec.getRecordId()).addFieldValues(rec.getFieldValues());
			}

			result.add(FormRecordsList.from(container, recMap.values()));
		}

		return result;
	}

	private FormDataEntryToken createDataEntryToken(Long formCtxtId, Long objectId) {
		if (formCtxtId == null) {
			throw OpenSpecimenException.userError(FormErrorCode.CTXT_ID_REQ);
		}

		if (objectId == null) {
			throw OpenSpecimenException.userError(FormErrorCode.OBJ_ID_REQ);
		}

		List<FormContextBean> formCtxts = formDao.getFormContextsById(Collections.singletonList(formCtxtId));
		if (CollectionUtils.isEmpty(formCtxts)) {
			throw OpenSpecimenException.userError(FormErrorCode.INVALID_FORM_CTXT, formCtxtId);
		}

		return createDataEntryToken(formCtxts.get(0), objectId);
	}

	private FormDataEntryToken createDataEntryToken(FormContextBean formCtxt, Long objectId) {
		CollectionProtocolRegistration cpr = daoFactory.getCprDao().getById(objectId);
		if (cpr == null) {
			throw OpenSpecimenException.userError(CprErrorCode.NOT_FOUND, objectId);
		}

		AccessCtrlMgr.getInstance().ensureUpdateCprRights(cpr);
		return createDataEntryToken(formCtxt, cpr);
	}

	private FormDataEntryToken createDataEntryToken(FormContextBean formCtxt, CollectionProtocolRegistration cpr) {
		Form form = formCtxt.getForm();
		if (formCtxt.isSysForm() && !isCollectionOrReceivedEvent(form.getName())) {
			throw OpenSpecimenException.userError(FormErrorCode.SYS_REC_EDIT_NOT_ALLOWED);
		}

		String entityType = formCtxt.getEntityType();
		if (!"Participant".equals(entityType) && !"CommonParticipant".equals(entityType)) {
			throw OpenSpecimenException.userError(FormErrorCode.OP_NOT_ALLOWED);
		}

		Calendar cal = Calendar.getInstance();
		Date now = cal.getTime();

		Integer linkAge = ConfigUtil.getInstance().getIntSetting(ConfigParams.MODULE, ConfigParams.PDE_LINK_AGE, 5);
		cal.add(Calendar.DATE, linkAge);
		Date expiryTime = cal.getTime();

		FormDataEntryToken deToken = new FormDataEntryToken();
		deToken.setFormCtxt(formCtxt);
		deToken.setObjectId(cpr.getId());
		deToken.setToken(UUID.randomUUID().toString());
		deToken.setCreatedBy(AuthUtil.getCurrentUser());
		deToken.setCreationTime(now);
		deToken.setExpiryTime(expiryTime);
		deToken.setStatus(FormDataEntryToken.Status.PENDING);

		deDaoFactory.getFormDataEntryTokenDao().saveOrUpdate(deToken);
		return deToken;
	}

	private Specimen ensureSpecimenUpdateRights(Long objectId, boolean checkPhiAccess) {
		Specimen specimen = daoFactory.getSpecimenDao().getById(objectId);
		if (specimen == null) {
			throw OpenSpecimenException.userError(SpecimenErrorCode.NOT_FOUND, objectId);
		} else if (!specimen.isEditAllowed()) {
			throw OpenSpecimenException.userError(SpecimenErrorCode.EDIT_NOT_ALLOWED, specimen.getLabel());
		}

		AccessCtrlMgr.getInstance().ensureCreateOrUpdateSpecimenRights(specimen, checkPhiAccess);
		return specimen;
	}

	private Function<ExportJob, List<? extends Object>> getFormRecordsGenerator() {
		return new Function<ExportJob, List<? extends Object>>() {
			private boolean endOfRecords = false;

			private boolean paramsInited = false;

			private Container form;

			private boolean formHasPhi;

			private String entityType;

			private CollectionProtocol cp;

			private Long cpId;

			private Set<SiteCpPair> siteCps;

			private int startAt;

			private Object lastObj;

			private Long lastObjId;

			private boolean lastObjReadAllowed;

			private boolean lastObjPhiAllowed;

			@Override
			public List<? extends Object> apply(ExportJob job) {
				if (endOfRecords) {
					return Collections.emptyList();
				}

				if (!paramsInited) {
					initParams(job);
				}


				Function<ExportJob, List<Map<String, Object>>> recordsFn = null;
				if (entityType.equals("Participant") || entityType.equals("CommonParticipant")) {
					recordsFn = this::getRegistrationFormRecords;
				} else if (entityType.equals("SpecimenCollectionGroup")) {
					recordsFn = this::getVisitFormRecords;
				} else if (entityType.equals("Specimen") || entityType.equals("SpecimenEvent")) {
					recordsFn = this::getSpmnFormRecords;
				}

				List<Map<String, Object>> records = null;
				if (recordsFn != null) {
					while (!endOfRecords && CollectionUtils.isEmpty(records)) {
						records = recordsFn.apply(job);
					}
				}

				return records;
			}

			private void initParams(ExportJob job) {
				Map<String, String> params = job.getParams();
				if (params == null) {
					params = Collections.emptyMap();
				}

				form = getContainer(null, params.get("formName"));
				formHasPhi = form.hasPhiFields();

				entityType = params.get("entityType");
				if (StringUtils.isBlank(entityType)) {
					throw OpenSpecimenException.userError(FormErrorCode.ENTITY_TYPE_REQUIRED);
				}

				String cpIdStr = params.get("cpId");
				if (StringUtils.isNotBlank(cpIdStr)) {
					try {
						cpId = Long.parseLong(cpIdStr);
					} catch (Exception e) {
						logger.error("Invalid CP ID: " + cpIdStr, e);
					}
				}

				boolean allowed = false;
				if (entityType.equals("Participant") || entityType.equals("CommonParticipant")) {
					if (cpId != null && cpId != -1L) {
						allowed = AccessCtrlMgr.getInstance().hasCprEximRights(cpId);
					} else {
						siteCps = AccessCtrlMgr.getInstance().getSiteCps(Resource.PARTICIPANT, Operation.EXIM);
						if (siteCps != null && siteCps.isEmpty()) {
							siteCps = AccessCtrlMgr.getInstance().getSiteCps(Resource.PARTICIPANT_DEID, Operation.EXIM);
						}

						allowed = (siteCps == null || !siteCps.isEmpty());
					}
				} else if (entityType.equals("SpecimenCollectionGroup")) {
					if (cpId != null && cpId != -1L) {
						allowed = AccessCtrlMgr.getInstance().hasVisitEximRights(cpId);
					} else {
						siteCps = AccessCtrlMgr.getInstance().getSiteCps(Resource.VISIT, Operation.EXIM);
						allowed = (siteCps == null || !siteCps.isEmpty());
					}
				} else if (entityType.equals("Specimen") || entityType.equals("SpecimenEvent")) {
					if (cpId != null && cpId != -1L) {
						allowed = AccessCtrlMgr.getInstance().hasSpecimenEximRights(cpId);
					} else {
						siteCps = AccessCtrlMgr.getInstance().getSiteCps(Resource.SPECIMEN, Operation.EXIM);
						allowed = (siteCps == null || !siteCps.isEmpty());
					}
				}

				if (!allowed) {
					endOfRecords = true;
				}

				paramsInited = true;
			}

			private List<Map<String, Object>> getRegistrationFormRecords(ExportJob job) {
				String entityType = job.getParams().get("entityType");

				return getRecords(
					job,
					"ppids",
					(ppids) -> {
						if (entityType.equals("Participant")) {
							return formDao.getRegistrationRecords(cpId, siteCps, form.getId(), ppids, startAt, 100);
						} else {
							return formDao.getParticipantRecords(cpId, siteCps, form.getId(), ppids, startAt, 100);
						}
					},
					"cprId",
					(cprId) -> daoFactory.getCprDao().getById(cprId),
					(cpr) -> {
						if (entityType.equals("Participant")) {
							return AccessCtrlMgr.getInstance().ensureReadCprRights((CollectionProtocolRegistration) cpr);
						} else {
							return AccessCtrlMgr.getInstance().ensureReadParticipantRights(((CollectionProtocolRegistration) cpr).getParticipant().getId());
						}
					},
					(cprFormValueMap) -> {
						CollectionProtocolRegistration cpr = (CollectionProtocolRegistration) cprFormValueMap.first();
						Map<String, Object> valueMap = cprFormValueMap.second();

						Map<String, Object> formData = new HashMap<>();
						formData.put("recordId", valueMap.get("id"));
						formData.put("cpShortTitle", cpr.getCollectionProtocol().getShortTitle());
						formData.put("ppid", cpr.getPpid());
						formData.put("formValueMap", valueMap);
						return formData;
					}
				);
			}

			private List<Map<String, Object>> getVisitFormRecords(ExportJob job) {
				return getRecords(
					job,
					"visitNames",
					(visitNames) -> formDao.getVisitRecords(cpId, siteCps, form.getId(), visitNames, startAt, 100),
					"visitId",
					(visitId) -> daoFactory.getVisitsDao().getById(visitId),
					(visit) -> AccessCtrlMgr.getInstance().ensureReadVisitRights((Visit) visit, true),
					(visitFormValueMap) -> {
						Visit visit = (Visit) visitFormValueMap.first();
						Map<String, Object> valueMap = visitFormValueMap.second();

						Map<String, Object> formData = new HashMap<>();
						formData.put("recordId", valueMap.get("id"));
						formData.put("cpShortTitle", visit.getCollectionProtocol().getShortTitle());
						formData.put("visitName", visit.getName());
						formData.put("formValueMap", valueMap);
						return formData;
					}
				);
			}

			private List<Map<String, Object>> getSpmnFormRecords(ExportJob job) {
				return getRecords(
					job,
					"specimenLabels",
					(spmnLabels) -> formDao.getSpecimenRecords(cpId, siteCps, form.getId(), entityType, spmnLabels, startAt, 100),
					"spmnId",
					(specimenId) -> daoFactory.getSpecimenDao().getById(specimenId),
					(specimen) -> {
						AccessCtrlMgr.SpecimenAccessRights rights = AccessCtrlMgr.getInstance()
							.ensureReadSpecimenRights((Specimen) specimen, true);
						return rights.phiAccess;
					},
					(spmnFormValueMap) -> {
						Specimen specimen = (Specimen) spmnFormValueMap.first();
						Map<String, Object> valueMap = spmnFormValueMap.second();

						Map<String, Object> formData = new HashMap<>();
						formData.put("recordId", valueMap.get("id"));
						formData.put("cpShortTitle", specimen.getCollectionProtocol().getShortTitle());
						formData.put("specimenLabel", specimen.getLabel());
						formData.put("barcode", specimen.getBarcode());
						formData.put("formValueMap", valueMap);
						return formData;
					}
				);
			}

			private List<Map<String, Object>> getRecords(
				ExportJob job,
				String namesCsvVar,
				Function<List<String>, List<Map<String, Object>>> getFormRecs,
				String objIdVar,
				Function<Long, Object> getObj,
				Function<Object, Boolean> objAccessChecker,
				Function<Pair<Object, Map<String, Object>>, Map<String, Object>> toFormRec) {

				String namesCsv = job.getParams().get(namesCsvVar);
				List<String> names = null;
				if (StringUtils.isNotBlank(namesCsv)) {
					names = Utility.csvToStringList(namesCsv);
					endOfRecords = true;
				}

				List<Map<String, Object>> records = getFormRecs.apply(names);
				startAt += records.size();
				if (records.size() < 100) {
					endOfRecords = true;
				}

				List<Map<String, Object>> result = new ArrayList<>();
				for (Map<String, Object> record : records) {
					Long objId = (Long)record.get(objIdVar);
					if (objId.equals(lastObjId) && !lastObjReadAllowed) {
						continue;
					}

					if (!objId.equals(lastObjId)) {
						try {
							lastObj = getObj.apply(objId);
							lastObjId = objId;
							lastObjPhiAllowed = objAccessChecker.apply(lastObj);
							lastObjReadAllowed = true;
						} catch (OpenSpecimenException ose) {
							if (isAccessDeniedError(ose)) {
								lastObjReadAllowed = lastObjPhiAllowed = false;
								continue;
							}

							throw ose;
						}
					}

					Long recordId = (Long)record.get("recordId");
					result.add(toFormRec.apply(Pair.make(lastObj, getFormData(recordId, !lastObjPhiAllowed))));
				}

				return result;
			}

			private Map<String, Object> getFormData(Long recordId, boolean maskPhi) {
				FormData formData = formDataMgr.getFormData(form, recordId);

				if (formHasPhi && maskPhi) {
					formData.maskPhiFieldValues();
				}

				return formData.getFieldNameValueMap(true);
			}

			private boolean isAccessDeniedError(OpenSpecimenException ose) {
				return ose.containsError(RbacErrorCode.ACCESS_DENIED);
			}
		};
	}

	private Container getContainer(Long formId, String formName) {
		Object key = null;
		Container form = null;
		if (formId != null) {
			form = Container.getContainer(formId);
			key = formId;
		} else if (StringUtils.isNotBlank(formName)) {
			form = Container.getContainer(formName);
			key = formName;
		}

		if (key == null) {
			throw OpenSpecimenException.userError(FormErrorCode.NAME_REQUIRED);
		} else if (form == null) {
			throw OpenSpecimenException.userError(FormErrorCode.NOT_FOUND, key, 1);
		}

		return form;
	}

	private class PatientDataEntryTokenGenerator implements PdeTokenGenerator {
		private ThreadLocal<Map<Long, FormContextBean>> formCtxts = new ThreadLocal<Map<Long, FormContextBean>>() {
			protected Map<Long, FormContextBean> initialValue() {
				TransactionalThreadLocals.getInstance().register(this);
				return new HashMap<>();
			}
		};

		@Override
		public PdeTokenDetail generate(CollectionProtocolRegistration cpr, Map<String, Object> input) {
			Number formCtxtId = (Number) input.get("formCtxtId");
			if (formCtxtId == null) {
				throw OpenSpecimenException.userError(FormErrorCode.CTXT_ID_REQ);
			}

			FormContextBean formCtxt = formCtxts.get().get(formCtxtId.longValue());
			if (formCtxt == null) {
				List<FormContextBean> fcs = formDao.getFormContextsById(Collections.singletonList(formCtxtId.longValue()));
				if (fcs.isEmpty()) {
					throw OpenSpecimenException.userError(FormErrorCode.INVALID_FORM_CTXT, formCtxtId);
				}

				formCtxt = fcs.get(0);
				formCtxts.get().put(formCtxt.getIdentifier(), formCtxt);
			}

			FormDataEntryToken token = createDataEntryToken(formCtxt, cpr);
			return tokenDetail(cpr, token);
		}

		@Override
		public PdeTokenDetail getToken(CollectionProtocolRegistration cpr, Long tokenId) {
			FormDataEntryToken token = deDaoFactory.getFormDataEntryTokenDao().getById(tokenId);
			return tokenDetail(cpr, token);
		}

		@Override
		public List<PdeTokenDetail> getTokens(List<Long> tokenIds) {
			List<PdeTokenDetail> tokens = deDaoFactory.getFormDataEntryTokenDao().getTokens(tokenIds);
			tokens.forEach(token -> token.setType(PDE_FORM_TYPE));
			return tokens;
		}

		private PdeTokenDetail tokenDetail(CollectionProtocolRegistration cpr, FormDataEntryToken token) {
			PdeTokenDetail result = new PdeTokenDetail();
			result.setCprId(cpr.getId());
			result.setCpShortTitle(cpr.getCpShortTitle());
			result.setPpid(cpr.getPpid());
			result.setType(PDE_FORM_TYPE);
			result.setFormCaption(token.getFormCtxt().getForm().getCaption());
			result.setTokenId(token.getId());
			result.setToken(token.getToken());
			result.setDataEntryLink(ConfigUtil.getInstance().getAppUrl() + "/#/patient-data-entry?token=" + token.getToken());
			result.setCreationTime(token.getCreationTime());
			result.setExpiryTime(token.getExpiryTime());
			result.setCompletionTime(token.getCompletionTime());
			return result;
		}
	}
}
