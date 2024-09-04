/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2024 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.businessproject.web;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.businessproject.db.InvoicingProject;
import com.axelor.apps.businessproject.exception.BusinessProjectExceptionMessage;
import com.axelor.apps.businessproject.service.InvoicingProjectService;
import com.axelor.apps.businessproject.service.ProjectBusinessService;
import com.axelor.apps.businessproject.service.ProjectHistoryService;
import com.axelor.apps.businessproject.translation.ITranslation;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.common.StringUtils;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ProjectController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void generateQuotation(ActionRequest request, ActionResponse response) {
    try {
      Project project = request.getContext().asType(Project.class);
      SaleOrder order = Beans.get(ProjectBusinessService.class).generateQuotation(project);
      response.setView(
          ActionView.define(I18n.get("Sale quotation"))
              .model(SaleOrder.class.getName())
              .add("form", "sale-order-form")
              .param("forceTitle", "true")
              .context("_showRecord", String.valueOf(order.getId()))
              .map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void generatePurchaseQuotation(ActionRequest request, ActionResponse response) {
    Project project = request.getContext().asType(Project.class);
    if (project.getId() != null) {
      response.setView(
          ActionView.define(I18n.get("Purchase Order"))
              .model(PurchaseOrder.class.getName())
              .add("form", "purchase-order-form")
              .add("grid", "purchase-order-quotation-grid")
              .param("search-filters", "purchase-order-filters")
              .context("_project", Beans.get(ProjectRepository.class).find(project.getId()))
              .map());
    }
  }

  public void countToInvoice(ActionRequest request, ActionResponse response) {

    Project project = request.getContext().asType(Project.class);

    int toInvoiceCount = 0;
    if (project.getId() != null) {
      toInvoiceCount = Beans.get(InvoicingProjectService.class).countToInvoice(project);
    }

    response.setValue("$toInvoiceCounter", toInvoiceCount);
  }

  public void showInvoicingProjects(ActionRequest request, ActionResponse response) {

    Project project = request.getContext().asType(Project.class);
    project = Beans.get(ProjectRepository.class).find(project.getId());

    response.setView(
        ActionView.define(I18n.get("Invoice Business Project"))
            .model(InvoicingProject.class.getName())
            .add("form", "invoicing-project-form")
            .param("forceEdit", "true")
            .param("show-toolbar", "false")
            .context("_project", project)
            .map());
  }

  public void getPartnerData(ActionRequest request, ActionResponse response) {
    Project project = request.getContext().asType(Project.class);
    Partner partner = project.getClientPartner();

    if (partner != null) {

      response.setValue("currency", partner.getCurrency());

      response.setValue(
          "priceList",
          project.getClientPartner() != null
              ? Beans.get(PartnerPriceListService.class)
                  .getDefaultPriceList(project.getClientPartner(), PriceListRepository.TYPE_SALE)
              : null);
    }
  }

  public void computeProjectTotals(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Project project = request.getContext().asType(Project.class);
    ProjectBusinessService projectBusinessService = Beans.get(ProjectBusinessService.class);
    projectBusinessService.computeProjectTotals(project);

    List<String> projectTaskList = projectBusinessService.checkPercentagesOver1000OnTasks(project);
    if (projectTaskList.isEmpty()) {
      response.setNotify(I18n.get(BusinessProjectExceptionMessage.PROJECT_UPDATE_TOTALS_SUCCESS));
    } else {
      response.setAlert(
          String.format(
                  I18n.get(ITranslation.PROJECT_TASK_FOLLOW_UP_VALUES_TOO_HIGH), projectTaskList)
              + I18n.get(BusinessProjectExceptionMessage.PROJECT_UPDATE_TOTALS_SUCCESS));
    }
    response.setReload(true);
  }

  public void getProjectTimeFollowUpData(ActionRequest request, ActionResponse response)
      throws AxelorException {
    String id = Optional.ofNullable(request.getData().get("id")).map(Object::toString).orElse("");

    if (StringUtils.isBlank(id)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          BusinessProjectExceptionMessage.PROJECT_REPORT_NO_ID_FOUND);
    }
    Map<String, Object> data =
        Beans.get(ProjectBusinessService.class)
            .processRequestToDisplayTimeReporting(Long.valueOf(id));
    response.setData(List.of(data));
  }

  public void getProjectFinancialFollowUpData(ActionRequest request, ActionResponse response)
      throws AxelorException {
    String id = Optional.ofNullable(request.getData().get("id")).map(Object::toString).orElse("");

    if (StringUtils.isBlank(id)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          BusinessProjectExceptionMessage.PROJECT_REPORT_NO_ID_FOUND);
    }

    Map<String, Object> data =
        Beans.get(ProjectBusinessService.class)
            .processRequestToDisplayFinancialReporting(Long.valueOf(id));
    response.setData(List.of(data));
  }

  public void getProjectHistoryData(ActionRequest request, ActionResponse response)
      throws AxelorException {
    String id = Optional.ofNullable(request.getData().get("id")).map(Object::toString).orElse("");

    if (StringUtils.isBlank(id)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          BusinessProjectExceptionMessage.PROJECT_REPORT_NO_ID_FOUND);
    }
    Map<String, Object> data =
        Beans.get(ProjectHistoryService.class)
            .processRequestToDisplayProjectHistory(Long.valueOf(id));
    response.setData(List.of(data));
  }
}
