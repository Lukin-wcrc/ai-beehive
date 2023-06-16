package cn.beehive.base.enums;

import lombok.AllArgsConstructor;

/**
 * @author hncboy
 * @date 2023/5/11
 * 系统参数 key 枚举
 */
@AllArgsConstructor
public enum SysParamKeyEnum {

    ;

    /**
     * paramKey
     */
    private final String paramKey;

    /**
     * 获取 ParamKey
     *
     * @return ParamKey
     */
    public String getParamKey() {
        // 转小写
        return paramKey.toLowerCase();
    }
}