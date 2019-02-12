package com.krishagni.catissueplus.core.common.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AbstractEvent;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.beans.factory.InitializingBean;

import com.krishagni.catissueplus.core.biospecimen.repository.DaoFactory;
import com.krishagni.catissueplus.core.common.PlusTransactional;
import com.krishagni.catissueplus.core.common.domain.SearchEntityKeyword;
import com.krishagni.catissueplus.core.common.events.SearchResult;
import com.krishagni.catissueplus.core.common.repository.SearchEntityKeywordDao;
import com.krishagni.catissueplus.core.common.service.SearchEntityKeywordProvider;
import com.krishagni.catissueplus.core.common.service.SearchResultProcessor;
import com.krishagni.catissueplus.core.common.service.SearchService;
import com.krishagni.catissueplus.core.common.util.AuthUtil;

public class SearchServiceImpl implements SearchService, InitializingBean {
	private SessionFactory sessionFactory;

	private DaoFactory daoFactory;

	private Map<String, SearchEntityKeywordProvider> entityKeywordProviders = new HashMap<>();

	private Map<String, SearchResultProcessor> resultProcessors = new HashMap<>();

	private Map<Transaction, KeywordProcessor> processors = new HashMap<>();

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	public void setDaoFactory(DaoFactory daoFactory) {
		this.daoFactory = daoFactory;
	}

	public void setEntityKeywordProviders(List<SearchEntityKeywordProvider> providers) {
		providers.forEach(provider -> entityKeywordProviders.put(provider.getEntity(), provider));
	}

	public void setResultProcessors(List<SearchResultProcessor> processors) {
		processors.forEach(processor -> resultProcessors.put(processor.getEntity(), processor));
	}

	@Override
	@PlusTransactional
	public List<SearchResult> search(String searchTerm, int maxResults) {
		if (StringUtils.isBlank(searchTerm)) {
			return Collections.emptyList();
		}

		if (AuthUtil.isAdmin()) {
			return daoFactory.getSearchEntityKeywordDao().getMatches(searchTerm, maxResults)
				.stream().map(SearchResult::from).collect(Collectors.toList());
		}

		List<String> entities = daoFactory.getSearchEntityKeywordDao().getMatchingEntities(searchTerm);
		List<SearchResult> results = new ArrayList<>();
		for (String entity : entities) {
			SearchResultProcessor proc = resultProcessors.get(entity);
			if (proc == null) {
				continue;
			}

			long lastId = -1;
			boolean moreMatches = true;
			while (moreMatches && results.size() < maxResults) {
				List<SearchResult> matches = proc.search(searchTerm, lastId, maxResults);
				moreMatches = matches.size() >= maxResults;

				Map<Long, SearchResult> dedupMap = new LinkedHashMap<>();
				for (SearchResult match : matches) {
					dedupMap.putIfAbsent(match.getEntityId(), match);
					lastId = match.getEntityId();
				}

				results.addAll(dedupMap.values());
			}

			if (results.size() >= maxResults) {
				break;
			}
		}

		return results;
	}

	@Override
	public void registerKeywordProvider(SearchEntityKeywordProvider provider) {
		entityKeywordProviders.put(provider.getEntity(), provider);
	}

	@Override
	public void registerSearchResultProcessor(SearchResultProcessor processor) {
		resultProcessors.put(processor.getEntity(), processor);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		EventListenerRegistry reg = ((SessionFactoryImpl) sessionFactory).getServiceRegistry().getService(EventListenerRegistry.class);

		EntityEventListener listener = new EntityEventListener();
		reg.getEventListenerGroup(EventType.POST_INSERT).appendListener(listener);
		reg.getEventListenerGroup(EventType.POST_UPDATE).appendListener(listener);
		reg.getEventListenerGroup(EventType.POST_DELETE).appendListener(listener);
	}

	private void addKeywords(AbstractEvent event, List<SearchEntityKeyword> keywords) {
		if (keywords == null || keywords.isEmpty()) {
			return;
		}

		Transaction txn = event.getSession().getTransaction();
		KeywordProcessor processor = processors.get(txn);
		if (processor == null) {
			processor = new KeywordProcessor();
			processors.put(txn, processor);

			event.getSession().getActionQueue().registerProcess(
				(session) -> {
					final KeywordProcessor process = processors.get(txn);
					if (process != null) {
						process.process(session);
						session.flush();
					}
				}
			);

			event.getSession().getActionQueue().registerProcess(
				(session) -> {
					processors.remove(txn);
				}
			);
		}

		processor.addKeywords(keywords);
	}

	private class EntityEventListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {
		@Override
		public void onPostInsert(PostInsertEvent event) {
			SearchEntityKeywordProvider provider = entityKeywordProviders.get(event.getEntity().getClass().getName());
			if (provider == null) {
				return;
			}

			addKeywords(event, provider.getKeywords(event));
		}

		@Override
		public void onPostUpdate(PostUpdateEvent event) {
			SearchEntityKeywordProvider provider = entityKeywordProviders.get(event.getEntity().getClass().getName());
			if (provider == null) {
				return;
			}

			addKeywords(event, provider.getKeywords(event));
		}

		@Override
		public void onPostDelete(PostDeleteEvent event) {
			SearchEntityKeywordProvider provider = entityKeywordProviders.get(event.getEntity().getClass().getName());
			if (provider == null) {
				return;
			}

			addKeywords(event, provider.getKeywords(event));
		}

		@Override
		public boolean requiresPostCommitHanding(EntityPersister persister) {
			return false;
		}
	}

	private class KeywordProcessor {
		private List<SearchEntityKeyword> keywords = new ArrayList<>();

		public void addKeywords(List<SearchEntityKeyword> keywords) {
			this.keywords.addAll(keywords);
		}

		public void process(SessionImplementor session) {
			SearchEntityKeywordDao keywordDao = daoFactory.getSearchEntityKeywordDao();

			for (SearchEntityKeyword keyword : keywords) {
				if (StringUtils.isNotBlank(keyword.getValue())) {
					keyword.setValue(keyword.getValue().toLowerCase());
				}

				SearchEntityKeyword existing = null;
				switch (keyword.getOp()) {
					case 0:
						saveKeyword(keyword);
						break;

					case 1:
						existing = getFromDb(keyword);
						if (existing == null) {
							saveKeyword(keyword);
						} else {
							existing.update(keyword);
							if (StringUtils.isBlank(keyword.getValue())) {
								keywordDao.delete(existing);
							}
						}
						break;

					case 2:
						existing = getFromDb(keyword);
						if (existing != null) {
							keywordDao.delete(existing);
						}
						break;
				}
			}
		}

		private SearchEntityKeyword getFromDb(SearchEntityKeyword keyword) {
			List<SearchEntityKeyword> keywords = daoFactory.getSearchEntityKeywordDao()
				.getKeywords(keyword.getEntity(), keyword.getEntityId(), keyword.getKey());

			return keywords.stream().filter(ex -> ex.getValue().equalsIgnoreCase(keyword.getOldValue())).findFirst().orElse(null);
		}

		private void saveKeyword(SearchEntityKeyword keyword) {
			if (StringUtils.isBlank(keyword.getValue())) {
				return;
			}

			daoFactory.getSearchEntityKeywordDao().saveOrUpdate(keyword);
		}
	}
}