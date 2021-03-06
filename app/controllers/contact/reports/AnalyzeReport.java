/**
* Copyright (c) 2015 Mustafa DUMLUPINAR, mdumlupinar@gmail.com
*
* This file is part of seyhan project.
*
* seyhan is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/
package controllers.contact.reports;

import static play.data.Form.form;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import meta.Balance;
import models.Contact;
import models.GlobalPrivateCode;
import models.GlobalTransPoint;
import play.data.Form;
import play.data.format.Formats.DateTime;
import play.data.validation.Constraints.Required;
import play.data.validation.ValidationError;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Result;
import reports.ReportParams;
import reports.ReportService;
import reports.ReportService.ReportResult;
import utils.AuthManager;
import utils.CacheUtils;
import utils.DateUtils;
import utils.InstantSQL;
import utils.QueryUtils;
import views.html.contacts.reports.analyze_report;
import controllers.global.Profiles;
import enums.Module;
import enums.ReportUnit;
import enums.Right;
import enums.RightLevel;

/**
 * @author mdpinar
*/
public class AnalyzeReport extends Controller {

	private final static Right RIGHT_SCOPE = Right.CARI_ANALIZ_RAPORU;
	private final static String REPORT_NAME = "Analyze";

	private final static Form<AnalyzeReport.Parameter> parameterForm = form(AnalyzeReport.Parameter.class);

	public static class Parameter {

		@Required
		public Contact contact;
		
		public ReportUnit unit;

		public GlobalTransPoint transPoint = Profiles.chosen().gnel_transPoint;
		public GlobalPrivateCode privateCode = Profiles.chosen().gnel_privateCode;

		@Required
		public String excCode;

		@Required
		@DateTime(pattern = "dd/MM/yyyy")
		public Date startDate = DateUtils.getFirstDayOfYear();
		
		@Required
		@DateTime(pattern = "dd/MM/yyyy")
		public Date endDate = new Date();

	}

	private static String getQueryString(Parameter params) {
		StringBuilder queryBuilder = new StringBuilder("");

		queryBuilder.append(" and t.workspace = " + CacheUtils.getWorkspaceId());

		if (params.contact != null && params.contact.id != null) {
			queryBuilder.append(" and t.contact_id = ");
			queryBuilder.append(params.contact.id);
		}

		if (params.startDate != null) {
			queryBuilder.append(" and t.trans_date >= ");
			queryBuilder.append(DateUtils.formatDateForDB(params.startDate));
		}
		
		if (params.endDate != null) {
			queryBuilder.append(" and t.trans_date <= ");
			queryBuilder.append(DateUtils.formatDateForDB(params.endDate));
		}

		if (params.excCode != null && ! params.excCode.isEmpty()) {
			queryBuilder.append(" and t.exc_code = '");
			queryBuilder.append(params.excCode);
			queryBuilder.append("'");
		}
		
		return queryBuilder.toString();
	}

	public static Result generate() {
		Result hasProblem = AuthManager.hasProblem(RIGHT_SCOPE, RightLevel.Enable);
		if (hasProblem != null) return hasProblem;

		Form<AnalyzeReport.Parameter> filledForm = parameterForm.bindFromRequest();
		
		if(filledForm.hasErrors()) {
			return badRequest(analyze_report.render(filledForm));
		} else {

			Parameter params = filledForm.get();

			if (params.contact.id == null) {
				List<ValidationError> veList = new ArrayList<ValidationError>();
				veList.add(new ValidationError("contact.name", Messages.get("is.not.null", Messages.get("contact"))));
				filledForm.errors().put("contact.name", veList);
				return badRequest(analyze_report.render(filledForm));
			}
			
			ReportParams repPar = new ReportParams();
			repPar.modul = RIGHT_SCOPE.module.name();
			repPar.reportName = REPORT_NAME;
			repPar.reportUnit = params.unit;
			repPar.query = getQueryString(params);
			
			Contact contact = Contact.findById(params.contact.id);
			repPar.paramMap.put("CONTACT_CODE", contact.code);
			repPar.paramMap.put("CONTACT_NAME", contact.name);
			repPar.paramMap.put("CONTACT_PHONE", contact.phone);
			repPar.paramMap.put("CONTACT_MOBILE_PHONE", contact.mobilePhone);

			Balance balance = QueryUtils.findBalance(params.startDate, Module.contact, params.contact.id, params.excCode);
			repPar.paramMap.put("EXC_CODE", params.excCode);
			repPar.paramMap.put("CONTACT_TRANSFER", balance.getTransfer());
			repPar.paramMap.put("CONTACT_DEBT_SUM", balance.getDebt());
			repPar.paramMap.put("CONTACT_CREDIT_SUM", balance.getCredit());
			repPar.paramMap.put("CONTACT_BALANCE", balance.getBalance());
			repPar.paramMap.put("CONTACT_EXC_CODE", params.excCode);

			repPar.paramMap.put("TRANS_POINT_SQL", "");
			if (params.transPoint != null && params.transPoint.id != null) {
				repPar.paramMap.put("TRANS_POINT_SQL", InstantSQL.buildTransPointSQL(params.transPoint.id));
			}

			repPar.paramMap.put("PRIVATE_CODE_SQL", "");
			if (params.privateCode != null && params.privateCode.id != null) {
				repPar.paramMap.put("PRIVATE_CODE_SQL", InstantSQL.buildPrivateCodeSQL(params.privateCode.id));
			}

			repPar.paramMap.put("REPORT_DATE", DateUtils.formatDateStandart(params.startDate) + " - " + DateUtils.formatDateStandart(params.endDate));
			
			ReportResult repRes = ReportService.generateReport(repPar, response());
			if (repRes.error != null) {
				flash("warning", repRes.error);
				return ok(analyze_report.render(filledForm));
			} else if (ReportService.isToDotMatrix(repPar)) {
				flash("success", Messages.get("printed.success"));
			}
			return ReportService.sendReport(repPar, repRes, analyze_report.render(filledForm));
		}

	}

	public static Result index() {
		Result hasProblem = AuthManager.hasProblem(RIGHT_SCOPE, RightLevel.Enable);
		if (hasProblem != null) return hasProblem;

		return ok(analyze_report.render(parameterForm.fill(new Parameter())));
	}

}
