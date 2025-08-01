/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
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
package com.axelor.apps.production.service;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.SaleOrderLineDetails;
import com.axelor.apps.production.db.repo.SaleOrderLineDetailsRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.service.MarginComputeService;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class SaleOrderLineDetailsPriceServiceImpl implements SaleOrderLineDetailsPriceService {

  protected final ProdProcessLineHourlyCostComputeService prodProcessLineHourlyCostComputeService;
  protected final AppBaseService appBaseService;
  protected final MarginComputeService marginComputeService;
  protected final ProductCompanyService productCompanyService;
  protected final AppSaleService appSaleService;
  protected final SaleOrderLineDetailsService saleOrderLineDetailsService;

  @Inject
  public SaleOrderLineDetailsPriceServiceImpl(
      ProdProcessLineHourlyCostComputeService prodProcessLineHourlyCostComputeService,
      AppBaseService appBaseService,
      MarginComputeService marginComputeService,
      ProductCompanyService productCompanyService,
      AppSaleService appSaleService,
      SaleOrderLineDetailsService saleOrderLineDetailsService) {
    this.prodProcessLineHourlyCostComputeService = prodProcessLineHourlyCostComputeService;
    this.appBaseService = appBaseService;
    this.marginComputeService = marginComputeService;
    this.productCompanyService = productCompanyService;
    this.appSaleService = appSaleService;
    this.saleOrderLineDetailsService = saleOrderLineDetailsService;
  }

  @Override
  public Map<String, Object> computePrices(
      SaleOrderLineDetails saleOrderLineDetails, SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException {
    Map<String, Object> lineMap = new HashMap<>();

    lineMap.putAll(computeCostPrice(saleOrderLineDetails, saleOrder, saleOrderLine));
    lineMap.putAll(computeSubTotalCostPrice(saleOrderLineDetails));
    lineMap.putAll(computePrice(saleOrderLineDetails));
    lineMap.putAll(computeTotalPrice(saleOrderLineDetails, saleOrder));

    return lineMap;
  }

  @Override
  public Map<String, Object> computePrice(SaleOrderLineDetails saleOrderLineDetails) {
    Map<String, Object> lineMap = new HashMap<>();
    BigDecimal marginCoefficient = saleOrderLineDetails.getMarginCoefficient();
    BigDecimal price;
    BigDecimal costPrice = saleOrderLineDetails.getCostPrice();

    price =
        marginCoefficient
            .multiply(costPrice)
            .setScale(appSaleService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);

    saleOrderLineDetails.setPrice(price);
    lineMap.put("price", saleOrderLineDetails.getPrice());
    return lineMap;
  }

  @Override
  public Map<String, Object> computeTotalPrice(
      SaleOrderLineDetails saleOrderLineDetails, SaleOrder saleOrder) throws AxelorException {
    Map<String, Object> lineMap = new HashMap<>();
    BigDecimal qty = saleOrderLineDetails.getQty();
    BigDecimal totalPrice =
        saleOrderLineDetails
            .getPrice()
            .multiply(qty)
            .setScale(appSaleService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
    saleOrderLineDetails.setTotalPrice(totalPrice);

    lineMap.put("totalPrice", saleOrderLineDetails.getTotalPrice());
    lineMap.putAll(
        marginComputeService.getComputedMarginInfo(
            saleOrder, saleOrderLineDetails, saleOrderLineDetails.getTotalPrice()));
    return lineMap;
  }

  protected Map<String, Object> computeCostPrice(
      SaleOrderLineDetails saleOrderLineDetails, SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException {
    Map<String, Object> lineMap = new HashMap<>();
    Company company = saleOrder.getCompany();
    Product product = saleOrderLineDetails.getProduct();

    int saleOrderLineDetailsTypeSelect = saleOrderLineDetails.getTypeSelect();
    BigDecimal costPrice = BigDecimal.ZERO;
    if (saleOrderLineDetailsTypeSelect == SaleOrderLineDetailsRepository.TYPE_COMPONENT) {
      if (product != null && company != null) {
        costPrice = (BigDecimal) productCompanyService.get(product, "costPrice", company);
      }
    } else if (saleOrderLineDetailsTypeSelect == SaleOrderLineDetailsRepository.TYPE_OPERATION) {
      ProdProcessLine prodProcessLine = saleOrderLineDetails.getProdProcessLine();
      BigDecimal qtyToProduce = saleOrderLine.getQtyToProduce();
      costPrice =
          prodProcessLineHourlyCostComputeService.computeLineHourlyCost(
              prodProcessLine, qtyToProduce);
    }

    saleOrderLineDetails.setCostPrice(
        costPrice.setScale(appBaseService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP));
    lineMap.put("costPrice", saleOrderLineDetails.getCostPrice());
    return lineMap;
  }

  protected Map<String, Object> computeSubTotalCostPrice(
      SaleOrderLineDetails saleOrderLineDetails) {
    Map<String, Object> lineMap = new HashMap<>();
    BigDecimal qty = saleOrderLineDetails.getQty();
    BigDecimal costPrice = saleOrderLineDetails.getCostPrice();
    BigDecimal totalCostPrice =
        costPrice
            .multiply(qty)
            .setScale(appSaleService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);

    saleOrderLineDetails.setSubTotalCostPrice(totalCostPrice);
    lineMap.put("subTotalCostPrice", saleOrderLineDetails.getSubTotalCostPrice());
    return lineMap;
  }

  @Override
  public Map<String, Object> computeMarginCoef(SaleOrderLineDetails saleOrderLineDetails) {
    Map<String, Object> lineMap = new HashMap<>();
    BigDecimal costPrice = saleOrderLineDetails.getCostPrice();
    BigDecimal marginCoef;
    if (costPrice.compareTo(BigDecimal.ZERO) == 0) {
      marginCoef = BigDecimal.ZERO;
    } else {
      BigDecimal price = saleOrderLineDetails.getPrice();
      marginCoef =
          price.divide(
              costPrice, appSaleService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
    }
    saleOrderLineDetails.setMarginCoefficient(marginCoef);
    lineMap.put("marginCoefficient", saleOrderLineDetails.getMarginCoefficient());
    return lineMap;
  }
}
