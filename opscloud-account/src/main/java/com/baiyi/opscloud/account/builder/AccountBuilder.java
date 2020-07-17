package com.baiyi.opscloud.account.builder;

import com.baiyi.opscloud.account.bo.AccountBO;
import com.baiyi.opscloud.common.base.AccountType;
import com.baiyi.opscloud.common.util.BeanCopierUtils;
import com.baiyi.opscloud.domain.generator.opscloud.OcAccount;
import org.gitlab.api.models.GitlabUser;


/**
 * @Author baiyi
 * @Date 2019/11/27 4:30 PM
 * @Version 1.0
 */
public class AccountBuilder {

    public static OcAccount build(GitlabUser gitlabUser) {
        AccountBO bo = AccountBO.builder()
                .accountId(gitlabUser.getId().toString())
                .username(gitlabUser.getUsername())
                .displayName(gitlabUser.getName())
                .email(gitlabUser.getEmail())
                .isActive("active".equals(gitlabUser.getState()))
                .accountType(AccountType.GITLAB.getType())
                .build();
        return convert(bo);
    }

    private static OcAccount convert(AccountBO bo) {
        return BeanCopierUtils.copyProperties(bo, OcAccount.class);
    }

}
