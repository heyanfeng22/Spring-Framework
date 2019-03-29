package com.gpSpringFreamWork.demo.controller;

import com.gpSpringFreamWork.annotation.GPController;
import com.gpSpringFreamWork.annotation.GPRequestMapping;
import com.gpSpringFreamWork.annotation.GPRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @Autor : heyanfeng22
 * @Description :
 * @Date:Create:in 2019/3/28 16:12
 * @Modified By:
 */
@GPController
@GPRequestMapping("/demo")
public class DemoController
{
    @GPRequestMapping("/getMessage")
    public void getMessage(HttpServletRequest request, HttpServletResponse response, @GPRequestParam("name") String name)
    {
        String result = "My name is " + name;
        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
