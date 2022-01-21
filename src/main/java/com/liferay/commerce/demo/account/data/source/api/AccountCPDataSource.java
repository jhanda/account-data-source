package com.liferay.commerce.demo.account.data.source.api;

import com.liferay.asset.kernel.model.AssetCategory;
import com.liferay.asset.kernel.model.AssetTag;
import com.liferay.asset.kernel.service.AssetCategoryLocalService;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.asset.kernel.service.AssetTagLocalService;
import com.liferay.commerce.account.model.CommerceAccount;
import com.liferay.commerce.account.model.CommerceAccountGroup;
import com.liferay.commerce.account.service.CommerceAccountGroupLocalService;
import com.liferay.commerce.constants.CommerceWebKeys;
import com.liferay.commerce.context.CommerceContext;
import com.liferay.commerce.product.catalog.CPQuery;
import com.liferay.commerce.product.data.source.CPDataSource;
import com.liferay.commerce.product.data.source.CPDataSourceResult;
import com.liferay.commerce.product.util.CPDefinitionHelper;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * @author Jeff Handa
 */
@Component(
        immediate = true,
        property = "commerce.product.data.source.name=" + AccountCPDataSource.NAME,
        service = CPDataSource.class
)
public class AccountCPDataSource implements CPDataSource {

    public static final String NAME = "account-data-source";

    @Override
    public String getLabel(Locale locale) {
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle(
                "content.Language", locale, getClass());

        return LanguageUtil.get(resourceBundle, "account-data-source");
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public CPDataSourceResult getResult(
            HttpServletRequest httpServletRequest, int start, int end)
            throws Exception {

        CommerceContext commerceContext = (CommerceContext)httpServletRequest.getAttribute(CommerceWebKeys.COMMERCE_CONTEXT);
        CommerceAccount commerceAccount = commerceContext.getCommerceAccount();
        long commerceAccountId = commerceAccount.getCommerceAccountId();
        String commerceAccountName = commerceAccount.getName();

        List<CommerceAccountGroup> commerceAccountGroups =
                _commerceAccountGroupLocalService.getCommerceAccountGroupsByCommerceAccountId(commerceAccountId);

        long companyId = _portal.getDefaultCompanyId();

        long globalGroupId = _groupLocalService.getCompanyGroup(companyId).getGroupId();

        Locale locale = httpServletRequest.getLocale();

        CPQuery cpQuery = _getCPQuery(
                globalGroupId, locale, commerceAccountName, commerceAccountGroups);

        return _cpDefinitionHelper.search(
                _portal.getScopeGroupId(httpServletRequest),
                new SearchContext() {
                    {
                        setAttributes(
                                HashMapBuilder.<String, Serializable>put(
                                        Field.STATUS, WorkflowConstants.STATUS_APPROVED
                                ).build());
                        setCompanyId(_portal.getCompanyId(httpServletRequest));
                        // setKeywords(StringPool.STAR + finalCustomerSegment);
                    }
                },
                cpQuery, start, end);
    }

    private CPQuery _getCPQuery(long groupId, Locale locale, String commerceAccountName,
                                List<CommerceAccountGroup> commerceAccountGroups) {

        ArrayList<String> commerceAccountGroupNames = new ArrayList<>();
        for (CommerceAccountGroup commerceAccountGroup : commerceAccountGroups){
            commerceAccountGroupNames.add(commerceAccountGroup.getName().toLowerCase(locale));
        }

        CPQuery cpQuery = new CPQuery();

        List<Long> tagIds = new ArrayList<>();

        List<AssetTag> assetTags = _assetTagLocalService.getAssetTags(
                QueryUtil.ALL_POS, QueryUtil.ALL_POS);

        for (AssetTag assetTag : assetTags){
            String name = assetTag.getName();

            if (name.equalsIgnoreCase(commerceAccountName) ||
                    commerceAccountGroupNames.contains(name)){

                tagIds.add(assetTag.getTagId());

                _log.debug(
                        "Adding tag name=[" + name + "] tagId=[" +
                                assetTag.getTagId() + "]");
            }
        }

        cpQuery.setAnyTagIds(
                tagIds.stream(
                ).mapToLong(
                        l -> l
                ).toArray());

        return cpQuery;
    }

    private static final Log _log = LogFactoryUtil.getLog(
            AccountCPDataSource.class);

    @Reference
    private CommerceAccountGroupLocalService _commerceAccountGroupLocalService;

    @Reference
    private AssetTagLocalService _assetTagLocalService;

    @Reference
    private CPDefinitionHelper _cpDefinitionHelper;

    @Reference
    private GroupLocalService _groupLocalService;

    @Reference
    private Portal _portal;

}