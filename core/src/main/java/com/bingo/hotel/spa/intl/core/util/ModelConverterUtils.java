package com.bingo.hotel.spa.intl.core.util;

import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ModelConverterUtils {

    /**
     * 创建类的一个实例
     *
     * @param beanClass
     *            类
     */
    public static <T> T newInstance(Class<T> beanClass) {
        try {
            return beanClass.newInstance();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 属性拷贝
     * @param source 源对象
     * @param target 目标对象
     */
    public static void copyProperties(Object source, Object target){
        if (source == null) {
            return;
        }
        BeanUtils.copyProperties(source, target);
    }

    /**
     * 对象转换
     * @param source 源对象
     * @param targetClass 目标对象
     * @param <T> 目标对象class类型
     * @return 返回新的目标对象
     */
    public static <T> T convert(Object source, Class<T> targetClass) {
        if(source == null ){
            return null;
        }
        T target = newInstance(targetClass);
        copyProperties(source, target);
        return target;
    }

    /**
     * 对象List转换
     * @param sources 源对象
     * @param targetClass 目标对象
     * @param <T> 目标对象class类型
     * @return 返回新的目标对象List
     */
    public static <T> List<T> convert(Collection<?> sources, Class<T> targetClass){
        if(sources == null ){
            return null;
        }
        List<T> targets = Lists.newArrayList();
        if (!CollectionUtils.isEmpty(sources)) {
            targets = sources.stream().map(x ->  convert(x, targetClass)).collect(Collectors.toList());
        }
        return targets;
    }

}
