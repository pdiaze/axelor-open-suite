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
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.BillOfMaterialLine;
import com.axelor.apps.production.db.ProdProcess;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.ProdProduct;
import com.axelor.apps.production.db.repo.ProdProcessRepository;
import com.axelor.apps.production.exceptions.ProductionExceptionMessage;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.JPA;
import com.axelor.i18n.I18n;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProdProcessService {

  protected ProdProcessRepository prodProcessRepo;

  @Inject
  public ProdProcessService(ProdProcessRepository prodProcessRepo) {
    this.prodProcessRepo = prodProcessRepo;
  }

  public void validateProdProcess(ProdProcess prodProcess, BillOfMaterial bom)
      throws AxelorException {
    Map<Product, BigDecimal> bomMap = new HashMap<Product, BigDecimal>();

    List<BillOfMaterialLine> billOfMaterialLines = bom.getBillOfMaterialLineList();
    for (BillOfMaterialLine boml : billOfMaterialLines) {
      bomMap.put(boml.getProduct(), boml.getQty());
    }
    List<ProdProcessLine> listPPL =
        MoreObjects.firstNonNull(prodProcess.getProdProcessLineList(), Collections.emptyList());
    for (ProdProcessLine prodProcessLine : listPPL) {
      List<ProdProduct> listPP =
          MoreObjects.firstNonNull(
              prodProcessLine.getToConsumeProdProductList(), Collections.emptyList());
      for (ProdProduct prodProduct : listPP) {
        if (!bomMap.containsKey(prodProduct.getProduct())) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(ProductionExceptionMessage.PROD_PROCESS_USELESS_PRODUCT),
              prodProduct.getProduct().getName());
        }
        bomMap.put(
            prodProduct.getProduct(),
            bomMap.get(prodProduct.getProduct()).subtract(prodProduct.getQty()));
      }
    }
    Set<Product> keyList = bomMap.keySet();
    Map<Product, BigDecimal> copyMap = new HashMap<Product, BigDecimal>();
    List<String> nameProductList = new ArrayList<String>();
    for (Product product : keyList) {
      if (bomMap.get(product).compareTo(BigDecimal.ZERO) > 0) {
        copyMap.put(product, bomMap.get(product));
        nameProductList.add(product.getName());
      }
    }
    if (!copyMap.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(ProductionExceptionMessage.PROD_PROCESS_MISS_PRODUCT),
          Joiner.on(",").join(nameProductList));
    }
  }

  public ProdProcess changeProdProcessListOutsourcing(ProdProcess prodProcess) {
    for (ProdProcessLine prodProcessLine : prodProcess.getProdProcessLineList()) {
      prodProcessLine.setOutsourcing(prodProcess.getOutsourcing());
      prodProcessLine.setOutsourcable(false);
    }
    return prodProcess;
  }

  public String getLanguageToPrinting(ProdProcess prodProcess) {

    User user = AuthUtils.getUser();

    String language = "en";

    if (user != null && !Strings.isNullOrEmpty(user.getLanguage())) {
      return user.getLanguage();
    }

    if (prodProcess == null) {
      return language;
    }
    Company company = prodProcess.getCompany();

    if (company != null && company.getPartner() != null) {
      language = ReportSettings.getPrintingLocale(company.getPartner());
    }

    return language;
  }

  @Transactional
  public ProdProcess generateNewVersion(ProdProcess prodProcess) {

    ProdProcess copy = prodProcessRepo.copy(prodProcess, true);

    copy.getProdProcessLineList().forEach(list -> list.setProdProcess(copy));
    copy.setOriginalProdProcess(prodProcess);
    copy.setVersionNumber(
        this.getLatestProdProcessVersion(prodProcess, prodProcess.getVersionNumber(), true) + 1);
    return prodProcessRepo.save(copy);
  }

  public int getLatestProdProcessVersion(ProdProcess prodProcess, int latestVersion, boolean deep) {
    List<ProdProcess> prodProcessSet = Lists.newArrayList();
    ProdProcess up = prodProcess;
    Long previousId = Long.valueOf(0);
    do {

      prodProcessSet =
          prodProcessRepo
              .all()
              .filter("self.originalProdProcess = :origin AND self.id != :id")
              .bind("origin", up)
              .bind("id", previousId)
              .order("-versionNumber")
              .fetch();
      if (!prodProcessSet.isEmpty()) {
        latestVersion =
            (prodProcessSet.get(0).getVersionNumber() > latestVersion)
                ? prodProcessSet.get(0).getVersionNumber()
                : latestVersion;
        for (ProdProcess prodProcessIterator : prodProcessSet) {
          int search = this.getLatestProdProcessVersion(prodProcessIterator, latestVersion, false);
          latestVersion = (search > latestVersion) ? search : latestVersion;
        }
      }
      previousId = up.getId();
      up = up.getOriginalProdProcess();
    } while (up != null && deep);

    return latestVersion;
  }

  @Transactional(rollbackOn = {Exception.class})
  public ProdProcess createCustomizedProdProcess(SaleOrderLine saleOrderLine) {
    ProdProcess prodProcess = saleOrderLine.getProdProcess();
    return createCustomizedProdProcess(prodProcess, true);
  }

  @Transactional(rollbackOn = {Exception.class})
  public ProdProcess createCustomizedProdProcess(ProdProcess prodProcess, boolean deep) {

    if (prodProcess == null) {
      return null;
    }

    long noOfPersonalizedProdProcess =
        prodProcessRepo
                .all()
                .filter("self.product = :product AND self.isPersonalized = true")
                .bind("product", prodProcess.getProduct())
                .count()
            + 1;
    ProdProcess personalizedProdProcess = JPA.copy(prodProcess, deep);
    String name =
        personalizedProdProcess.getName()
            + " ("
            + I18n.get(ProductionExceptionMessage.BOM_1)
            + " "
            + noOfPersonalizedProdProcess
            + ")";
    personalizedProdProcess.setName(name);
    personalizedProdProcess.setFullName(name);
    personalizedProdProcess.setIsPersonalized(true);

    return prodProcessRepo.save(personalizedProdProcess);
  }
}
