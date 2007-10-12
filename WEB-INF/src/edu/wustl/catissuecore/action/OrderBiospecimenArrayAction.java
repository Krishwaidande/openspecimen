
package edu.wustl.catissuecore.action;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import edu.wustl.catissuecore.actionForm.OrderBiospecimenArrayForm;
import edu.wustl.catissuecore.actionForm.OrderForm;
import edu.wustl.catissuecore.bizlogic.BizLogicFactory;
import edu.wustl.catissuecore.bizlogic.DistributionBizLogic;
import edu.wustl.catissuecore.domain.DistributionProtocol;
import edu.wustl.catissuecore.domain.Specimen;
import edu.wustl.catissuecore.domain.SpecimenArray;
import edu.wustl.catissuecore.domain.SpecimenArrayType;
import edu.wustl.catissuecore.util.global.Constants;
import edu.wustl.common.action.BaseAction;
import edu.wustl.common.beans.NameValueBean;
import edu.wustl.common.bizlogic.IBizLogic;
import edu.wustl.common.util.dbManager.DAOException;

public class OrderBiospecimenArrayAction extends BaseAction
{
	/**
	 * @param mapping ActionMapping object
	 * @param form ActionForm object
	 * @param request HttpServletRequest object
	 * @param response HttpServletResponse object
	 * @return ActionForward object
	 * @throws Exception object
	 */
	public ActionForward executeAction(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response)
			throws Exception
	{
		OrderBiospecimenArrayForm arrayObject = (OrderBiospecimenArrayForm) form;
		HttpSession session = request.getSession();

		if (session.getAttribute("OrderForm") != null)
		{
			OrderForm orderForm = (OrderForm) session.getAttribute("OrderForm");
			arrayObject.setOrderForm(orderForm);

			if (orderForm.getDistributionProtocol() != null)
			{
				getProtocolName(request, arrayObject, orderForm);
			}

			List specimenArrayList = getDataFromDatabase(request);
			request.setAttribute("SpecimenNameList", specimenArrayList);

			request.setAttribute("typeOf", "specimenArray");
			request.setAttribute("OrderBiospecimenArrayForm", arrayObject);

			return mapping.findForward("success");
		}
		else
		{
			return mapping.findForward("failure");
		}
	}

	/**
	 * @param request HttpServletRequest object
	 * @param arrayObject OrderBiospecimenArrayForm object
	 * @param orderForm OrderForm object
	 * @throws Exception object
	 */
	private void getProtocolName(HttpServletRequest request, OrderBiospecimenArrayForm arrayObject, OrderForm orderForm) throws Exception
	{
		// to get the distribution protocol name
		DistributionBizLogic dao = (DistributionBizLogic) BizLogicFactory.getInstance().getBizLogic(Constants.DISTRIBUTION_FORM_ID);

		String sourceObjectName = DistributionProtocol.class.getName();
		String[] displayName = {"title"};
		String valueField = Constants.SYSTEM_IDENTIFIER;
		List protocolList = dao.getList(sourceObjectName, displayName, valueField, true);

		request.setAttribute(Constants.DISTRIBUTIONPROTOCOLLIST, protocolList);

		for (int i = 0; i < protocolList.size(); i++)
		{
			NameValueBean obj = (NameValueBean) protocolList.get(i);

			if (orderForm.getDistributionProtocol().equals(obj.getValue()))
			{
				arrayObject.setDistrbutionProtocol(obj.getName());
			}
		}
	}

	/**
	 * @param request HttpServletRequest object
	 * @return List specimen array objects
	 */
	private List getDataFromDatabase(HttpServletRequest request) throws DAOException
	{
		//		// to get data from database when specimen id is given
		//		IBizLogic bizLogic = BizLogicFactory.getInstance().getBizLogic(Constants.NEW_SPECIMEN_FORM_ID);
		//		HttpSession session = request.getSession(true);
		//		
		//		List arrayList = new ArrayList();
		//		//retriving the id list from session.
		//		if(session.getAttribute("arrayIdList") != null)
		//		{
		//			List idList = (List)session.getAttribute("arrayIdList");	    	
		//			for(int i=0;i<idList.size();i++)
		//			{
		//				List arrayListFromDb = bizLogic.retrieve(SpecimenArray.class.getName(), "id", (String)idList.get(i));
		//				SpecimenArray specimenArray = (SpecimenArray)arrayListFromDb.get(0);
		//				arrayList.add(specimenArray);
		//			}
		//		}
		//		return arrayList;

		IBizLogic bizLogic = BizLogicFactory.getInstance().getBizLogic(Constants.NEW_SPECIMEN_FORM_ID);
		HttpSession session = request.getSession(true);
		
		String sourceObjectName = SpecimenArray.class.getName();
		String columnName="id";
    	List valueField=(List)session.getAttribute(Constants.SPECIMEN_ARRAY_ID);
    	
    	List specimenArrayList=new ArrayList();
    	if(valueField != null && valueField.size() >0)
    	{
			for(int i=0;i<valueField.size();i++)
			{
				List SpecimenArray = bizLogic.retrieve(sourceObjectName, columnName, (String)valueField.get(i));
				SpecimenArray specArray=(SpecimenArray)SpecimenArray.get(0);
				specimenArrayList.add(specArray);
			}
    	}
    	
    	
    	
    	
    	
    	
	//	String[] columnName = {"name"};
	//	List specimenArrayList = bizLogic.retrieve(sourceObjectName);
		if (specimenArrayList != null && specimenArrayList.size() > 0)
		{
			Iterator itr = specimenArrayList.iterator();
			while (itr.hasNext())
			{
				SpecimenArray specimenArray = (SpecimenArray)itr.next();
				String[] whereColumnName = {"id"};
				String[] whereColumnCond = {"="};
				String[] selectColumnName =  {"specimenArrayType"};
				Object[] whereColumnValue = {specimenArray.getId()};

				List specimenTypeList = bizLogic.retrieve(sourceObjectName,selectColumnName, whereColumnName, whereColumnCond,
						whereColumnValue, Constants.AND_JOIN_CONDITION);
				if(specimenTypeList != null && specimenTypeList.size()>0)
				{
					SpecimenArrayType specimenArrayType = (SpecimenArrayType) specimenTypeList.get(0);
					
					Collection specimenTypeCollection = (Collection) bizLogic.retrieveAttribute(SpecimenArrayType.class.getName(),specimenArrayType.getId(),"elements(specimenTypeCollection)");
					if(specimenTypeCollection != null)
					{
						specimenArrayType.setSpecimenTypeCollection(specimenTypeCollection);
					}
					specimenArray.setSpecimenArrayType(specimenArrayType);
				}
				
				Collection specimenArrayContentCollection = (Collection)bizLogic.retrieveAttribute(sourceObjectName,specimenArray.getId(),"elements(specimenArrayContentCollection)");
				if(specimenArrayContentCollection != null)
				{
					specimenArray.setSpecimenArrayContentCollection(specimenArrayContentCollection);
				}
			}
		}
		return specimenArrayList;
	}
}
