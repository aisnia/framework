package com.xiaoqiang.ioc.factory;

import com.xiaoqiang.aop.advisor.Advisor;
import com.xiaoqiang.aop.advisor.RegexMatchAdvisor;
import com.xiaoqiang.aop.annotation.*;
import com.xiaoqiang.aop.bean.PointCutResover;
import com.xiaoqiang.aop.postprocessor.AopProxyCreator;
import com.xiaoqiang.ioc.annotation.Bean;
import com.xiaoqiang.ioc.annotation.Configuration;
import com.xiaoqiang.ioc.utils.*;
import com.xiaoqiang.mvc.annotation.Autowired;
import com.xiaoqiang.mvc.annotation.Controller;
import com.xiaoqiang.mvc.annotation.RequestMapping;
import com.xiaoqiang.mvc.bean.Request;
import com.xiaoqiang.mvc.bean.RequestHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;

/**
 * @author xiaoqiang
 * @date 2019/10/6-17:50
 */
public class ApplicationContext extends JSONBeanFactory {
    public Properties getProperties() {
        return properties;
    }

    private Properties properties = new Properties();

    private Set<Class<?>> classCache = new HashSet<>();

    private Map<Request, RequestHandler> mappingMap = new HashMap<>();

    public Map<Request, RequestHandler> getMappingMap() {
        return mappingMap;
    }

    private ApplicationContext() {
    }


    public
    ApplicationContext(String properties) {
        init(ClassLoaderUtils.getClassLoder().getResourceAsStream(properties));
    }


    private void init(InputStream in) {
        try {
            //加载配置文件
            properties.load(in);
            //json配置文件
            String jsonConfigs = properties.getProperty(Constant.JSONCONFIG);
            if (jsonConfigs != null) {
                String[] Configs = jsonConfigs.split(",");
                for (String config : Configs) {
                    loadBeanDefinitions(loadFile(config));
                }
            }
            String scanPackges = properties.getProperty(Constant.SCAN_PACKGE);
            if (scanPackges != null) {
                registerAnnotationBeans(scanPackges.split(","));
            }

            doCreateBeans();
            dealAnnotationBeans();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void dealAnnotationBeans() {
        for (Class<?> clazz : classCacheObjectsMap.keySet()) {


            String name = StringUtils.lowerFirstChar(clazz.getSimpleName());
            Object o = classCacheObjectsMap.get(clazz);
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                Autowired annotation = field.getAnnotation(Autowired.class);
                if (annotation != null) {
                    String value = annotation.value().trim();
                    if ("".equals(value)) {
                        ReflectionUtils.setField(o, field, classCacheObjectsMap.get(field.getType()));
                    } else {
                        ReflectionUtils.setField(o, field, singletonObjectsMap.get(value));
                    }
                }
            }
            singletonObjectsMap.put(name, o);
        }

    }

    private void
    doCreateBeans() {
        if (classCache.isEmpty()) {
            return;
        }
        for (Class<?> clazz : classCache) {
            if (clazz.getAnnotations().length == 0) {
                continue;
            } else if (clazz.isAnnotationPresent(Configuration.class)) {
                Object o = ReflectionUtils.newInstance(clazz);
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(Bean.class)) {
                        Bean beanAnnotation = method.getAnnotation(Bean.class);
                        String name = beanAnnotation.value();
                        if ("".equals(name.trim())) {
                            name = StringUtils.lowerFirstChar(method.getReturnType().getSimpleName());
                        }
                        Object bean = ReflectionUtils.invoke(method, o);
                        classCacheObjectsMap.put(bean.getClass(), bean);
                        singletonObjectsMap.put(name, bean);
                    }
                }
            } else if (clazz.isAnnotationPresent(Controller.class)) {
                String url = "";
                if (clazz.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping annotation = clazz.getAnnotation(RequestMapping.class);
                    url = annotation.value();
                }
                Object o = ReflectionUtils.newInstance(clazz);

                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(RequestMapping.class)) {
                        RequestMapping annotation = method.getAnnotation(RequestMapping.class);
                        Request request = new Request(annotation.method(), annotation.value());
                        RequestHandler handler = new RequestHandler(o, method);
                        mappingMap.put(request, handler);
                    }
                }
                classCacheObjectsMap.put(clazz, o);
            } else if (clazz.isAnnotationPresent(Aspect.class)) {
                addPostProcessor(clazz);
            } else {
                Object o = ReflectionUtils.newInstance(clazz);

                classCacheObjectsMap.put(clazz, o);
            }

        }
    }

    private void addPostProcessor(Class<?> clazz) {
        AopProxyCreator aopProxyCreator = new AopProxyCreator();
        Method[] methods = clazz.getMethods();
        Object o = ReflectionUtils.newInstance(clazz);
        aopProxyCreator.setObj(o);
        PointCutResover pointCutResover = new PointCutResover();
        Map<String, String> expressions = new HashMap<>();
        for (Method method : methods) {
            if (method.isAnnotationPresent(PointCut.class)) {
                //加入切点
                PointCut pointCut = method.getDeclaredAnnotation(PointCut.class);
                String expression = pointCut.value();
                expressions.put(method.getName(), expression);
            } else if (method.isAnnotationPresent(Before.class)) {
                Before before = method.getAnnotation(Before.class);
                String value = before.value();
                RegexMatchAdvisor beforeAdvice = new RegexMatchAdvisor("BeforeAdvice", expressions.get(value), pointCutResover,method);
                aopProxyCreator.register(beforeAdvice);
            }else if (method.isAnnotationPresent(After.class)) {
                After after = method.getAnnotation(After.class);
                String value = after.value();
                RegexMatchAdvisor afterAdvice = new RegexMatchAdvisor("AfterAdvice", expressions.get(value), pointCutResover, method);
                aopProxyCreator.register(afterAdvice);
            }else if (method.isAnnotationPresent(Around.class)) {
                Around around = method.getAnnotation(Around.class);
                String value = around.value();
                RegexMatchAdvisor aroundAdvice = new RegexMatchAdvisor("AroundAdvice", expressions.get(value), pointCutResover, method);
                aopProxyCreator.register(aroundAdvice);
            }
        }
        aopPostProcessors.add(aopProxyCreator);

    }

    private void registerAnnotationBeans(String[] scanPackges) {
        for (String scanPackge : scanPackges) {
            classCache.addAll(ClassLoaderUtils.scanPackge(scanPackge));
        }
    }


}
