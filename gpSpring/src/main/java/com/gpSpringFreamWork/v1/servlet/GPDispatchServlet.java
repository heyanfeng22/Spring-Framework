package com.gpSpringFreamWork.v1.servlet;

import com.gpSpringFreamWork.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Autor : heyanfeng22
 * @Description :
 * @Date:Create:in 2019/3/27 11:23
 * @Modified By:
 */
public class GPDispatchServlet extends HttpServlet
{
    /**
     * 配置文件
     */
    private Properties contextConfig = new Properties();

    /**
     * 扫描包下所有类名集合
     */
    private List<String> className = new ArrayList<String>();

    /**
     * IOC容器
     */
    private Map<String,Object> ioc = new HashMap<String,Object>();

    /**
     * url访问地址和Method关联的集合
     */
    private List<Handler> handlerMappping = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        try
        {
            dispatch(req,resp);
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void dispatch(HttpServletRequest req, HttpServletResponse resp) throws InvocationTargetException, IllegalAccessException, IOException, ServletException
    {
        //根据形参获取实参数组
        //给每个实参赋值
        //利用反射调用方法
        Handler handler = getHandler(req);

        if(handler == null){
//        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found!!!");
            return;
        }



        Class[] paramTypes = handler.getParamTypes();
        Object[] realParams = new Object[paramTypes.length];

        Map<String,String[]> map = req.getParameterMap();

        for (Map.Entry<String, String[]> param :map.entrySet())
        {
            //
            if(!handler.getParamIndexMapping().containsKey(param.getKey()))
            {
                continue;
            }
            //String value = (String)param.getValue();

            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]","")
                    .replaceAll("\\s",",");

            int indexParam = handler.paramIndexMapping.get(param.getKey());
            realParams[indexParam] = convert(paramTypes[indexParam],value);
        }

        if(handler.getParamIndexMapping().containsKey(HttpServletRequest.class.getName()))
        {
            int indexReq =    handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            realParams[indexReq] = req;
        }

        if(handler.getParamIndexMapping().containsKey(HttpServletResponse.class.getName()))
        {
            int indexResp =    handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            realParams[indexResp] = resp;
        }

        //反射调用方法
        Method method = handler.method;
        Object returnValue =  method.invoke(handler.controller,realParams);

        if(returnValue ==null ||returnValue instanceof Void){return;}

        resp.getWriter().write(returnValue.toString());

    }

    //url传过来的参数都是String类型的，HTTP是基于字符串协议
    //只需要把String转换为任意类型就好
    private Object convert(Class<?> type,String value){
        //如果是int
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        else if(Double.class == type){
            return Double.valueOf(value);
        }
        //如果还有double或者其他类型，继续加if
        //这时候，我们应该想到策略模式了
        //在这里暂时不实现，希望小伙伴自己来实现
        return value;
    }

    private Handler getHandler(HttpServletRequest req)
    {
        //解析request，获取url
        //绝对路径处理成相对路径
        if(handlerMappping.isEmpty()){return null;}

        //getRequestURI、getServletPath、getContextPath、getRealPath的区别
        //http://localhost:8080/news/main/list.jsp  访问地址,news是项目名称
        //getRequestURI----------->/news/main/list.jsp
        //getServletPath---------->/main/list.jsp
        //getContextPath---------->/news
        //getRealPath("/")------------->F:\Tomcat 6.0\webapps\news\test

        //我们的目的是要获得main/list.jsp
        String uri = req.getRequestURI();
        String contextPath = req.getContextPath();

        Handler returnHandler = null;


        uri = uri.replaceAll(contextPath,"").replaceAll("/+","/");






        for (Handler handler :handlerMappping)
        {
            Matcher matcher = handler.getPattern().matcher(uri);
            if(matcher.matches())
            {
                returnHandler = handler;
                break;
            }
        }

        return returnHandler;

    }

    @Override
    public void destroy()
    {
        super.destroy();
    }



    @Override
    public void init(ServletConfig config) throws ServletException
    {
        //加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //扫描配置文件中的所有类,把所有的类名放到一个List中
        doScanner(contextConfig.getProperty("scanPackage"));
        //初始化类，放入IOC容器
        doInstance();

        //完成依赖注入
        doAutoWired();
        //配置HandlerMapping
        initHandlerMapping();

        System.out.println("init 方法执行完成");
    }

    private void initHandlerMapping()
    {
        //遍历IOC对象，查找配置GPRequestMapping的方法
        //把RequestMapping里面的url和method引用一起放handler对象里
        if(ioc.isEmpty()){return;}
        for (Map.Entry entry :ioc.entrySet())
        {
            Object obj = entry.getValue();

            //查找GPController注解的类
            if(obj.getClass().isAnnotationPresent(GPController.class))
            {

                //获取类上面的RequestMapping
                String classPath = obj.getClass().getAnnotation(GPRequestMapping.class).value();


                //找出所有的public的方法
                Method[] methods = obj.getClass().getMethods();

                //遍历，找出方法上的RequestMapping
                for (Method method :methods)
                {
                    if(method.isAnnotationPresent(GPRequestMapping.class))
                    {
                        //找自定义方法名
                        String url ="/"+classPath+"/" +method.getAnnotation(GPRequestMapping.class).value();

                        //老版的是直接把这个url和method放入handlerMapping
                        //因为参数顺序不定，可能产生了问题
                        //handlerMapping.put(url,method);

                        //把url里面的多余的//替换成一个/
                        String regix =url.replaceAll("/+","/");
                        Pattern pattern = Pattern.compile(regix);

                        //tomcat启动的时候，初始化访问方法

                        this.handlerMappping.add(new Handler(pattern,obj,method));




                    }
                }

            }
        }


    }

    private void doAutoWired()
    {
        if(ioc.isEmpty())
        {
            return;
        }

        //遍历IOC容器，给实例中的所有AutoWired属性赋值
        //先找自定义名，再找首字母小写的，

        try
        {
            for (Map.Entry entry : ioc.entrySet())
            {
                Object object = entry.getValue();
                Field[] fields = object.getClass().getDeclaredFields();
                for (Field field : fields)
                {

                    //被AutoWired注解标识，赋值
                    if (field.isAnnotationPresent(GPAutowired.class))
                    {
                        Object obj = null;

                        String fieldName = field.getAnnotation(GPAutowired.class).value();

                        obj = ioc.get(fieldName);

                        if ("".equals(fieldName))
                        {
                            //获取成员变量首字母小写的
                            obj = ioc.get(toFirstCharLower(field.getType().getName()));
                        }



                        //也可能没有自定义名字，用接口的类型引用
                        if (null == obj)
                        {
                            Class[] interfaces = field.getClass().getInterfaces();
                            //默认这里只有一个接口
                            obj = ioc.get(interfaces[0].getName());

                        }

                        //打开访问限制
                        field.setAccessible(true);

                        //给field赋值
                        field.set(object, obj);
                    }
                }
            }
        }catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void doInstance()
    {
        if(className.isEmpty())
        {
            return;
        }

        //所有的标注了GPController、GPService的类都要被实例化

        try{
        for (String className :className)
        {
            //如果是GPController
            Class clazz = Class.forName(className);
            if(clazz.isAnnotationPresent(GPController.class))
            {
                Object object = clazz.newInstance();
                //首字母小写
                String beanName = toFirstCharLower(clazz.getSimpleName());
                ioc.put(beanName,object);
            }
            else if(clazz.isAnnotationPresent(GPService.class))
            {
                //Service 因为涉及到依赖注入，所以一般有3种方式
                //1、自定义的 2、默认的首字母小写的 3、接口的
                GPService service = (GPService) clazz.getAnnotation(GPService.class);
                String beanName = service.value();

                if("".equals(beanName))
                {
                    //默认首字母小写的
                    beanName = toFirstCharLower(clazz.getSimpleName());
                }
                Object object = clazz.newInstance();
                ioc.put(beanName,object);

                //如果引入的是接口
                for (Class interClazz :clazz.getInterfaces())
                {
                    //其他的类也可能实现这个接口
                    if(ioc.containsKey(interClazz.getName()))
                    {
                        throw new Exception("The “" + interClazz.getName() + "” is exists!!");
                    }

                    ioc.put(interClazz.getName(),object);
                }
            }
            else{
                continue;
            }

        }
        }catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private String toFirstCharLower(String className)
    {
        char[] chars = className.toCharArray();
        chars[0] +=32;
        return String.valueOf(chars);
    }

    private void doScanner(String scanPackage)
    {
        //scanPackage=com.gpSpringFreamWork.demo

        //把.换成路径/
        URL url = this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.","/"));
        File scanPackageFile = new File(url.getFile());

        for (File file: scanPackageFile.listFiles())
        {
            //如果是目录，递归，否则将文件名放到
            if(file.isDirectory()){doScanner(scanPackage+"."+file.getName());}
            else {
                //如果是class
                if(file.getName().endsWith("class"))
                {
                    String fileName = scanPackage+"."+file.getName().replace(".class","");
                    className.add(fileName);
                }
            }
        }




    }

    private void doLoadConfig(String initParameter)
    {
        InputStream in = GPDispatchServlet.class.getClassLoader().getResourceAsStream(initParameter);

        try
        {
            contextConfig.load(in);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if(null!=in)
            {
                try
                {
                    in.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 自定义一个Handler来保存url和method的关联方式
     */
    public class Handler
    {
        private Pattern pattern;

        private Object controller;

        private Method method;

        private Class[] paramTypes;

        private Map<String,Integer> paramIndexMapping;

        public Pattern getPattern()
        {
            return pattern;
        }

        public Object getObject()
        {
            return controller;
        }

        public Method getMethod()
        {
            return method;
        }

        public Class[] getParamTypes()
        {
            return paramTypes;
        }

        public Map<String, Integer> getParamIndexMapping()
        {
            return paramIndexMapping;
        }

        public Handler()
        {

        }

        public Handler(Pattern pattern, Object object, Method method)
        {
            this.pattern = pattern;
            this.controller = object;
            this.method = method;

            this.paramTypes = method.getParameterTypes();

            this.paramIndexMapping = new HashMap<String,Integer>();

            putParamIndexMapping(method);

        }

        private void putParamIndexMapping(Method method)
        {
            //正常参数
            Annotation[][] an = method.getParameterAnnotations();

            System.out.println("annotation数组参数的个数是----------"+an.length);
            for (int i=0;i<an.length;i++)
            {
                for (Annotation a :an[i])
                {
                    if(a instanceof GPRequestParam)
                    {
                        String paramName = ((GPRequestParam) a).value();
                        if(!"".equals(paramName))
                        {
                            this.paramIndexMapping.put(paramName,i);
                        }
                    }
                }
            }


            Class[] paramTypes = method.getParameterTypes();
            System.out.println("getParameterTypes数组参数的个数是----------"+paramTypes.length);
            //把request，response找出来
            for(int i=0;i<paramTypes.length;i++)
            {
                if(paramTypes[i]==HttpServletRequest.class||paramTypes[i]==HttpServletResponse.class)
                {
                    this.paramIndexMapping.put(paramTypes[i].getName(),i);
                }
            }



        }
    }


}
