package com.xiaoqiang.ioc.factory;

import com.xiaoqiang.aop.postprocessor.PostProcessor;
import com.xiaoqiang.ioc.bean.BeanDefinition;
import com.xiaoqiang.ioc.bean.ConstructorArg;
import com.xiaoqiang.ioc.bean.PropertyArg;
import com.xiaoqiang.ioc.utils.ClassLoaderUtils;
import com.xiaoqiang.ioc.utils.Constant;
import com.xiaoqiang.ioc.utils.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author xiaoqiang
 * @date 2019/10/5-11:17
 */
public abstract class AbstractBeanFactory implements BeanFactory {


    protected final ConcurrentHashMap<String, Object> singletonObjectsMap = new ConcurrentHashMap<>();

    protected final ConcurrentHashMap<String, BeanDefinition> beanDefinitionsMap = new ConcurrentHashMap<>();

    protected final Map<Class<?>, Object> classCacheObjectsMap = new HashMap<>();

    //记录观察者
    protected List<PostProcessor> aopPostProcessors = new ArrayList<>();


    @Override
    public Object getBean(String name) {
        Object bean = getSingleton(name);
        if (bean != null) {
            bean = applyAopBeanPostProcessor(bean, name);
            return bean;
        }
        BeanDefinition beanDefinition = beanDefinitionsMap.get(name);
        if (beanDefinition == null) {
            throw new RuntimeException("can't find bean " + name);
        }
        bean = createBean(beanDefinition);
        classCacheObjectsMap.put(bean.getClass(), bean);
        populateBean(bean, beanDefinition);
        singletonObjectsMap.put(beanDefinition.getName(), bean);

        //AOP处理
        bean = applyAopBeanPostProcessor(bean, name);
        return bean;
    }

    protected  Object applyAopBeanPostProcessor(Object bean, String name){
        try {
            for (PostProcessor aopPostProcessor : aopPostProcessors) {
                bean = aopPostProcessor.postProcessWeaving(bean, name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bean;
    }

    private void populateBean(Object bean, BeanDefinition beanDefinition) {
        List<PropertyArg> propertyArgs = beanDefinition.getPropertyArgs();
        Map<String, PropertyArg> propertyArgMap = new HashMap<>();
        if (propertyArgs != null) {
            for (PropertyArg propertyArg : propertyArgs) {
                propertyArgMap.put(propertyArg.getName(), propertyArg);
            }
        } else {
            return;
        }

        Field[] fields = bean.getClass().getDeclaredFields();

        for (Field field : fields) {
            PropertyArg propertyArg = propertyArgMap.get(field.getName());
            if (propertyArg != null && propertyArg.getValue() != null) {
                ReflectionUtils.setField(bean, field, propertyArg.getValue());
            } else {
                if (propertyArg.getRef() == null) {
                    throw new RuntimeException("can't populateBean");
                }
                ReflectionUtils.setField(bean, field, getBean(propertyArg.getRef()));
            }
        }
    }


    private Object createBean(BeanDefinition beanDefinition) {
        Class<?> clazz = ClassLoaderUtils.loadClass(beanDefinition.getClassName(), false);
        List<ConstructorArg> constructorArgs = beanDefinition.getConstructorArgs();
        if (constructorArgs != null) {

            List<Class<?>> classList = new ArrayList<>();
            List<Object> objectList = new ArrayList<>();
            for (ConstructorArg constructorArg : constructorArgs) {
                if (constructorArg != null) {
                    String value = constructorArg.getValue();
                    if (value != null) {
                        Class<?> c = Constant.CLASS_MAP.get(constructorArg.getType());
                        classList.add(c);
                        Object obj = ReflectionUtils.Cast(value, c);
                        objectList.add(obj);
                    } else {
                        String ref = constructorArg.getRef();
                        Object bean = getBean(ref);
                        if (bean != null) {
                            classList.add(bean.getClass());
                            objectList.add(bean);
                        }
                    }
                }
            }
            Class<?>[] classes = new Class[classList.size()];
            classList.toArray(classes);
            return ReflectionUtils.newInstance(clazz, classes, objectList.toArray());
        } else {
            return ReflectionUtils.newInstance(clazz);
        }
    }

    private Object getSingleton(String name) {
        return singletonObjectsMap.get(name);
    }

    protected void registerBean(String name, BeanDefinition bd) {
        beanDefinitionsMap.put(name, bd);
    }
    public Map<String, Object> getSingletonObjectsMap() {
        return singletonObjectsMap;
    }

    public Map<String, BeanDefinition> getBeanDefinitionsMap() {
        return beanDefinitionsMap;
    }

    public Map<Class<?>, Object> getClassCacheObjectsMap() {
        return classCacheObjectsMap;


    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) {
        return beanDefinitionsMap.get(beanName);
    }
}
