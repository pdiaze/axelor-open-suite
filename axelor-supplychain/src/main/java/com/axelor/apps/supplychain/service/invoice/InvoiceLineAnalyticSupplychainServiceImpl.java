/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2024 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.supplychain.service.invoice;

import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.AnalyticAccountRepository;
import com.axelor.apps.account.service.analytic.AnalyticMoveLineService;
import com.axelor.apps.account.service.analytic.AnalyticToolService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceLineAnalyticServiceImpl;
import com.axelor.apps.tool.service.ListToolService;
import com.google.inject.Inject;
import java.util.List;

public class InvoiceLineAnalyticSupplychainServiceImpl extends InvoiceLineAnalyticServiceImpl {

  @Inject
  public InvoiceLineAnalyticSupplychainServiceImpl(
      AnalyticAccountRepository analyticAccountRepository,
      AnalyticMoveLineService analyticMoveLineService,
      AnalyticToolService analyticToolService,
      AccountConfigService accountConfigService,
      ListToolService listToolService,
      AppAccountService appAccountService) {
    super(
        analyticAccountRepository,
        analyticMoveLineService,
        analyticToolService,
        accountConfigService,
        listToolService,
        appAccountService);
  }

  @Override
  public List<AnalyticMoveLine> createAnalyticDistributionWithTemplate(InvoiceLine invoiceLine) {
    List<AnalyticMoveLine> analyticMoveLineList =
        super.createAnalyticDistributionWithTemplate(invoiceLine);
    for (AnalyticMoveLine analyticMoveLine : analyticMoveLineList) {
      analyticMoveLine.setSaleOrderLine(invoiceLine.getSaleOrderLine());
      analyticMoveLine.setPurchaseOrderLine(invoiceLine.getPurchaseOrderLine());
    }
    return analyticMoveLineList;
  }
}
