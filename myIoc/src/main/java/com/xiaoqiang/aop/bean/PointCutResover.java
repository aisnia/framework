package com.xiaoqiang.aop.bean;

import com.xiaoqiang.ioc.utils.StringUtils;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * @author xiaoqiang
 * @date 2019/10/10-14:31
 */
public class PointCutResover implements PointCut {
    @Override
    public boolean matchsClass(Class<?> targetClass, String expression) throws Exception {
        String className = StringUtils.matchClassName(expression);
        //将.转换为普通的字符
        expression.replace(".", "\\.");
        expression.replace("*", ".*");
        //全路径
        String name = targetClass.getName();
        boolean matches = Pattern.matches(className, name);
        return matches;
    }

    @Override
    public boolean matchsMethod(Class<?> targetClass, Method method, String expression) throws Exception {
        boolean isMatch = matchsClass(targetClass, expression);
        if (!isMatch) {
            return false;
        }

        //匹配方法名
        String matchName = StringUtils.matchMethodName(expression);

        String methodName = method.getName();

        if ("*".equals(matchName)) {
            return true;
        }

        return Pattern.matches(matchName, methodName);
    }
}
