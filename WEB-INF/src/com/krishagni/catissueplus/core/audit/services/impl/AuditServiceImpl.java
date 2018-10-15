package com.krishagni.catissueplus.core.audit.services.impl;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.envers.AuditReaderFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.krishagni.catissueplus.core.administrative.domain.User;
import com.krishagni.catissueplus.core.administrative.domain.factory.UserErrorCode;
import com.krishagni.catissueplus.core.audit.domain.UserApiCallLog;
import com.krishagni.catissueplus.core.audit.domain.factory.AuditErrorCode;
import com.krishagni.catissueplus.core.audit.events.AuditDetail;
import com.krishagni.catissueplus.core.audit.events.AuditEntityQueryCriteria;
import com.krishagni.catissueplus.core.audit.events.FormDataRevisionDetail;
import com.krishagni.catissueplus.core.audit.events.RevisionDetail;
import com.krishagni.catissueplus.core.audit.events.RevisionEntityRecordDetail;
import com.krishagni.catissueplus.core.audit.repository.RevisionsListCriteria;
import com.krishagni.catissueplus.core.audit.services.AuditService;
import com.krishagni.catissueplus.core.biospecimen.repository.DaoFactory;
import com.krishagni.catissueplus.core.common.PlusTransactional;
import com.krishagni.catissueplus.core.common.errors.OpenSpecimenException;
import com.krishagni.catissueplus.core.common.events.ExportedFileDetail;
import com.krishagni.catissueplus.core.common.events.RequestEvent;
import com.krishagni.catissueplus.core.common.events.ResponseEvent;
import com.krishagni.catissueplus.core.common.service.ObjectAccessor;
import com.krishagni.catissueplus.core.common.service.ObjectAccessorFactory;
import com.krishagni.catissueplus.core.common.util.AuthUtil;
import com.krishagni.catissueplus.core.common.util.ConfigUtil;
import com.krishagni.catissueplus.core.common.util.CsvFileWriter;
import com.krishagni.catissueplus.core.common.util.CsvWriter;
import com.krishagni.catissueplus.core.common.util.EmailUtil;
import com.krishagni.catissueplus.core.common.util.MessageUtil;
import com.krishagni.catissueplus.core.common.util.Utility;
import com.krishagni.rbac.common.errors.RbacErrorCode;

public class AuditServiceImpl implements AuditService {

	private static Log logger = LogFactory.getLog(AuditServiceImpl.class);

	private static final int ONLINE_EXPORT_TIMEOUT_SECS = 30;

	private static final String REV_EMAIL_TMPL = "audit_entity_revisions";

	private DaoFactory daoFactory;

	private ObjectAccessorFactory objectAccessorFactory;

	private ThreadPoolTaskExecutor taskExecutor;

	public void setDaoFactory(DaoFactory daoFactory) {
		this.daoFactory = daoFactory;
	}

	public void setObjectAccessorFactory(ObjectAccessorFactory objectAccessorFactory) {
		this.objectAccessorFactory = objectAccessorFactory;
	}

	public void setTaskExecutor(ThreadPoolTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	@Override
	@PlusTransactional
	public ResponseEvent<List<AuditDetail>> getEntityAuditDetail(RequestEvent<List<AuditEntityQueryCriteria>> req) {
		return ResponseEvent.response(getEntityAuditDetail(req.getPayload()));
	}

	@Override
	@PlusTransactional
	public ResponseEvent<List<RevisionDetail>> getEntityRevisions(RequestEvent<List<AuditEntityQueryCriteria>> req) {
		List<AuditEntityQueryCriteria> criteria = req.getPayload();
		ensureReadAccess(criteria);

		List<RevisionDetail> revisions = criteria.stream().map(this::getEntityRevisions)
			.flatMap(Collection::stream)
			.collect(Collectors.toList());

		if (criteria.size() > 1) {
			Collections.sort(revisions, (r1, r2) -> r2.getChangedOn().compareTo(r1.getChangedOn()));
		}

		return ResponseEvent.response(revisions);
	}

	@Override
	@PlusTransactional
	public ResponseEvent<ExportedFileDetail> exportRevisions(RequestEvent<RevisionsListCriteria> req) {
		if (!AuthUtil.isAdmin()) {
			return ResponseEvent.userError(RbacErrorCode.ACCESS_DENIED);
		}

		RevisionsListCriteria criteria = req.getPayload();

		User user = null;
		if (criteria.userId() != null) {
			user = daoFactory.getUserDao().getById(criteria.userId());
			if (user == null) {
				return ResponseEvent.userError(UserErrorCode.NOT_FOUND, criteria.userId());
			}
		}

		Date startDate = Utility.chopSeconds(criteria.startDate());
		Date endDate   = Utility.getEndOfDay(criteria.endDate());
		Date endOfDay  = Utility.getEndOfDay(Calendar.getInstance().getTime());
		if (startDate != null && startDate.after(endOfDay)) {
			return ResponseEvent.userError(AuditErrorCode.DATE_GT_TODAY, Utility.getDateTimeString(startDate));
		}

		if (endDate != null && endDate.after(endOfDay)) {
			return ResponseEvent.userError(AuditErrorCode.DATE_GT_TODAY, Utility.getDateTimeString(endDate));
		}

		if (startDate != null && endDate != null) {
			long days = Utility.daysBetween(startDate, endDate);
			if (days > 30L) {
				return ResponseEvent.userError(AuditErrorCode.DATE_INTERVAL_GT_ALLOWED);
			}
		} else if (startDate != null) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(startDate);
			cal.add(Calendar.DAY_OF_MONTH, 30);
			endDate = cal.getTime().after(endOfDay) ? endOfDay : Utility.getEndOfDay(cal.getTime());
		} else {
			endDate = endDate != null ? endDate : endOfDay;

			Calendar cal = Calendar.getInstance();
			cal.setTime(endDate);
			cal.add(Calendar.DAY_OF_MONTH, -30);
			startDate = Utility.chopTime(cal.getTime());
		}

		criteria.startDate(startDate).endDate(endDate);

		User currentUser     = AuthUtil.getCurrentUser();
		User revisionsByUser = user;

		File revisionsFile = null;
		Future<File> result = taskExecutor.submit(() -> exportRevisions(criteria, currentUser, revisionsByUser));
		try {
			revisionsFile = result.get(ONLINE_EXPORT_TIMEOUT_SECS, TimeUnit.SECONDS);
		} catch (TimeoutException te) {
			// timed out waiting for the response
		} catch (OpenSpecimenException ose) {
			throw ose;
		} catch (Exception ie) {
			throw OpenSpecimenException.serverError(ie);
		}

		return ResponseEvent.response(new ExportedFileDetail(getFileId(revisionsFile), revisionsFile));
	}

	@Override
	public ResponseEvent<File> getExportedRevisionsFile(RequestEvent<String> req) {
		String filename = req.getPayload() + "_" + AuthUtil.getCurrentUser().getId();
		return ResponseEvent.response(new File(getAuditDir(), filename));
	}

	@Override
	@PlusTransactional
	public void insertApiCallLog(UserApiCallLog userAuditLog) {
		daoFactory.getAuditDao().saveOrUpdate(userAuditLog);
	}

	@Override
	@PlusTransactional
	public long getTimeSinceLastApiCall(Long userId, String token) {
		Date lastApiCallTime = daoFactory.getAuditDao().getLatestApiCallTime(userId, token);
		long timeSinceLastApiCallInMilli = Calendar.getInstance().getTime().getTime() - lastApiCallTime.getTime();
		return TimeUnit.MILLISECONDS.toMinutes(timeSinceLastApiCallInMilli);
	}

	private List<AuditDetail> getEntityAuditDetail(List<AuditEntityQueryCriteria> criteria) {
		ensureReadAccess(criteria);
		return criteria.stream().map(this::getEntityAuditDetail).collect(Collectors.toList());
	}

	private void ensureReadAccess(List<AuditEntityQueryCriteria> criteria) {
		for (AuditEntityQueryCriteria crit : criteria) {
			ObjectAccessor accessor = objectAccessorFactory.getAccessor(crit.getObjectName());
			if (accessor == null) {
				throw OpenSpecimenException.userError(AuditErrorCode.ENTITY_NOT_FOUND, crit.getObjectName());
			}

			accessor.ensureReadAllowed(crit.getObjectId());
		}
	}

	private AuditDetail getEntityAuditDetail(AuditEntityQueryCriteria crit) {
		ObjectAccessor accessor = objectAccessorFactory.getAccessor(crit.getObjectName());
		return daoFactory.getAuditDao().getAuditDetail(accessor.getAuditTable(), crit.getObjectId());
	}

	private List<RevisionDetail> getEntityRevisions(AuditEntityQueryCriteria crit) {
		ObjectAccessor accessor = objectAccessorFactory.getAccessor(crit.getObjectName());
		return daoFactory.getAuditDao().getRevisions(accessor.getAuditTable(), crit.getObjectId());
	}

	@PlusTransactional
	private File exportRevisions(RevisionsListCriteria criteria, User exportedBy, User revisionsBy) {
		AuthUtil.setCurrentUser(exportedBy);

		String baseDir = UUID.randomUUID().toString();
		Date exportedOn = Calendar.getInstance().getTime();
		File coreObjectsRevs = new CoreObjectsRevisionExporter(criteria, exportedBy, exportedOn, revisionsBy).export(baseDir);
		File formsDataRevs   = new FormsDataRevisionExporter(criteria, exportedBy, exportedOn, revisionsBy).export(baseDir);

		File result = new File(getAuditDir(), baseDir + "_" + getTs(exportedOn) + "_" + exportedBy.getId());
		List<String> inputFiles = Arrays.asList(coreObjectsRevs.getAbsolutePath(), formsDataRevs.getAbsolutePath());
		Utility.zipFiles(inputFiles, result.getAbsolutePath());

		cleanupRevisionsDir(baseDir);
		sendEmailNotif(criteria, exportedBy, revisionsBy, result);
		return result;
	}


	private File getAuditDir() {
		return new File(ConfigUtil.getInstance().getDataDir(), "audit");
	}

	private File getAuditDir(String dir) {
		File result = new File(getAuditDir(), dir);
		if (!result.exists()) {
			result.mkdirs();
		}

		return result;
	}

	private void cleanupRevisionsDir(String baseDir) {
		File dir = getAuditDir(baseDir);
		for (File file : dir.listFiles()) {
			file.delete();
		}

		dir.delete();
	}

	private class CoreObjectsRevisionExporter {
		private RevisionsListCriteria criteria;

		private User exportedBy;

		private Date exportedOn;

		private User revisionsBy;

		public CoreObjectsRevisionExporter(RevisionsListCriteria criteria, User exportedBy, Date exportedOn, User revisionsBy) {
			this.criteria = criteria;
			this.exportedBy = exportedBy;
			this.exportedOn = exportedOn;
			this.revisionsBy = revisionsBy;
		}

		public File export(String dir) {
			long startTime = System.currentTimeMillis();
			CsvFileWriter csvWriter = null;

			try {
				File outputFile = getOutputFile(dir);
				csvWriter = CsvFileWriter.createCsvFileWriter(outputFile);

				writeHeader(csvWriter);

				long lastRecId = 0, totalRecords = 0, lastChunk = 0;
				Map<String, String> context = new HashMap<>();
				while (true) {
					long t1 = System.currentTimeMillis();
					List<RevisionDetail> revisions = daoFactory.getAuditDao().getRevisions(criteria);
					System.err.println(criteria.lastId() + ", " + (System.currentTimeMillis() - t1) + " ms");

					if (revisions.isEmpty()) {
						break;
					}

					for (RevisionDetail revision : revisions) {
						totalRecords += writeRows(context, revision, csvWriter);
						lastRecId = revision.getLastRecordId();

						long currentChunk = totalRecords / 25;
						if (currentChunk != lastChunk) {
							csvWriter.flush();
							lastChunk = currentChunk;
						}
					}

					criteria.lastId(lastRecId);
				}

				csvWriter.flush();
				return outputFile;
			} catch (Exception e) {
				logger.error("Error exporting core objects' revisions", e);
				throw OpenSpecimenException.serverError(e);
			} finally {
				IOUtils.closeQuietly(csvWriter);
				logger.info("Core objects' revisions export finished in " +  (System.currentTimeMillis() - startTime) + " ms");
			}
		}

		private File getOutputFile(String dir) {
			return new File(getAuditDir(dir), "os_core_objects_revisions_" + getTs(exportedOn) + ".csv");
		}

		private void writeHeader(CsvWriter writer) {
			writeExportHeader(writer, criteria, exportedBy, exportedOn, revisionsBy);

			String[] keys = {
				"audit_rev_id", "audit_rev_tstmp", "audit_rev_user", "audit_rev_user_email",
				"audit_rev_entity_op", "audit_rev_entity_name", "audit_rev_entity_id",
				"audit_rev_change_log"
			};
			writer.writeNext(Stream.of(keys).map(MessageUtil.getInstance()::getMessage).toArray(String[]::new));
		}

		//
		// Row format
		// revision number, rev date, user, rev type, entity name, entity id
		//
		private int writeRows(Map<String, String> context, RevisionDetail revision, CsvWriter writer)
			throws IOException {
			String revId     = revision.getRevisionId().toString();
			String dateTime  = Utility.getDateTimeString(revision.getChangedOn());

			String user      = null;
			String userEmail = null;
			if (revision.getChangedBy() != null) {
				user      = revision.getChangedBy().formattedName();
				userEmail = revision.getChangedBy().getEmailAddress();
			}

			Function<String, String> toMsg = AuditServiceImpl.this::toMsg;
			int recsCount = 0;

			for (RevisionEntityRecordDetail record : revision.getRecords()) {
				if (StringUtils.isBlank(record.getModifiedProps()) && record.getType() != 2) {
					continue;
				}

				String op = null;
				switch (record.getType()) {
					case 0:
						op = "audit_op_insert";
						break;

					case 1:
						op = "audit_op_update";
						break;

					case 2:
						op = "audit_op_delete";
						break;
				}

				String opDisplay  = context.computeIfAbsent(op, toMsg);
				String entityName = context.computeIfAbsent("audit_entity_" + record.getEntityName(), toMsg);
				String entityId   = record.getEntityId().toString();

				writer.writeNext(new String[] {revId, dateTime, user, userEmail, opDisplay, entityName, entityId, record.getModifiedProps()});
				++recsCount;

				if (recsCount % 25 == 0) {
					writer.flush();
				}
			}

			return recsCount;
		}
	}

	private class FormsDataRevisionExporter {
		private RevisionsListCriteria criteria;

		private User exportedBy;

		private Date exportedOn;

		private User revisionsBy;

		public FormsDataRevisionExporter(RevisionsListCriteria criteria, User exportedBy, Date exportedOn, User revisionsBy) {
			this.criteria = criteria;
			this.exportedBy = exportedBy;
			this.exportedOn = exportedOn;
			this.revisionsBy = revisionsBy;
		}

		public File export(String dir) {
			long startTime = System.currentTimeMillis();
			CsvFileWriter csvWriter = null;

			try {
				File outputFile = getOutputFile(dir);
				csvWriter = CsvFileWriter.createCsvFileWriter(outputFile);

				writeHeader(csvWriter);

				long lastRevId = 0, totalRecords = 0;
				Map<String, String> context = new HashMap<>();

				criteria.lastId(null);
				while (true) {
					long t1 = System.currentTimeMillis();

					List<FormDataRevisionDetail> revisions = daoFactory.getAuditDao().getFormDataRevisions(criteria);
					System.err.println(criteria.lastId() + ", " + (System.currentTimeMillis() - t1) + " ms");

					if (revisions.isEmpty()) {
						break;
					}

					for (FormDataRevisionDetail revision : revisions) {
						writeRow(context, revision, csvWriter);
						lastRevId = revision.getId();
						++totalRecords;

						if (totalRecords % 25 == 0) {
							csvWriter.flush();
						}
					}

					criteria.lastId(lastRevId);
				}

				csvWriter.flush();
				return outputFile;
			} catch (Exception e) {
				logger.error("Error exporting forms data revisions", e);
				throw OpenSpecimenException.serverError(e);
			} finally {
				IOUtils.closeQuietly(csvWriter);
				logger.info("Forms data revisions export finished in " +  (System.currentTimeMillis() - startTime) + " ms");
			}
		}

		private File getOutputFile(String dir) {
			return new File(getAuditDir(dir), "os_forms_data_revisions_" + getTs(exportedOn) + ".csv");
		}

		private void writeHeader(CsvWriter writer) {
			writeExportHeader(writer, criteria, exportedBy, exportedOn, revisionsBy);

			String[] keys = {
				"audit_rev_id", "audit_rev_tstmp", "audit_rev_user", "audit_rev_user_email",
				"audit_rev_entity_op", "audit_rev_entity_name", "audit_rev_form_name",
				"audit_rev_parent_entity_id", "audit_rev_entity_id"
			};
			writer.writeNext(Stream.of(keys).map(MessageUtil.getInstance()::getMessage).toArray(String[]::new));
		}

		private void writeRow(Map<String, String> context, FormDataRevisionDetail revision, CsvWriter writer) {
			String revId     = revision.getId().toString();
			String dateTime  = Utility.getDateTimeString(revision.getTime());

			String user      = null;
			String userEmail = null;
			if (revision.getUser() != null) {
				user      = revision.getUser().formattedName();
				userEmail = revision.getUser().getEmailAddress();
			}

			Function<String, String> toMsg = AuditServiceImpl.this::toMsg;

			String op = "audit_op_" + revision.getOp().toLowerCase();
			String opDisplay = context.computeIfAbsent(op, toMsg);

			String entityType = context.computeIfAbsent("form_entity_" + revision.getEntityType(), toMsg);
			String entityId = revision.getEntityId() != null ? revision.getEntityId().toString() : null;

			String formName = revision.getFormName();
			String recordId = revision.getRecordId().toString();

			writer.writeNext(new String[] {
				revId, dateTime, user, userEmail,
				opDisplay, entityType, formName, entityId, recordId
			});
		}
	}

	private void sendEmailNotif(RevisionsListCriteria criteria, User exportedBy, User revsBy, File revisionsFile) {
		Map<String, Object> emailProps = new HashMap<>();
		emailProps.put("startDate", getDateTimeString(criteria.startDate()));
		emailProps.put("endDate",   getDateTimeString(criteria.endDate()));
		emailProps.put("user",      revsBy != null ? revsBy.formattedName() : null);
		emailProps.put("fileId",    getFileId(revisionsFile));
		emailProps.put("rcpt",      exportedBy.formattedName());

		EmailUtil.getInstance().sendEmail(
			REV_EMAIL_TMPL,
			new String[] { exportedBy.getEmailAddress() },
			null,
			emailProps
		);
	}

	private String getFileId(File revisionsFile) {
		if (revisionsFile == null) {
			return null;
		}

		return revisionsFile.getName().substring(0, revisionsFile.getName().lastIndexOf("_"));
	}

	private String getDateTimeString(Date dt) {
		return dt != null ? Utility.getDateTimeString(dt) : null;
	}

	private void writeExportHeader(CsvWriter writer, RevisionsListCriteria criteria, User exportedBy, Date exportedOn, User revisionUser) {
		writeRow(writer, toMsg("audit_rev_exported_by"), exportedBy.formattedName());
		writeRow(writer, toMsg("audit_rev_exported_on"), getDateTimeString(exportedOn));

		if (revisionUser != null) {
			writeRow(writer, toMsg("audit_rev_audited_user"), revisionUser.formattedName());
		}

		if (criteria.startDate() != null) {
			writeRow(writer, toMsg("audit_rev_start_date"), getDateTimeString(criteria.startDate()));
		}

		if (criteria.endDate() != null) {
			writeRow(writer, toMsg("audit_rev_end_date"), getDateTimeString(criteria.endDate()));
		}

		writeRow(writer, "");
	}

	private void writeRow(CsvWriter writer, String ... columns) {
		writer.writeNext(columns);
	}

	private String toMsg(String key) {
		try {
			int idx = key.indexOf("-");
			if (idx > 0) {
				key = key.substring(0, idx);
			}

			return MessageUtil.getInstance().getMessage(key);
		} catch (Exception e) {
			return key;
		}
	}

	private String getTs(Date date) {
		return new SimpleDateFormat("yyyyMMdd_HHmm").format(date);
	}
}