package com.gymwork.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.gymwork.api.support.IntegrationTest.Session;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Atalhos para chamar a API autenticado como um usuário nos testes de integração. */
public final class ApiClient {

    private final MockMvc mvc;
    private final Session session;

    public ApiClient(MockMvc mvc, Session session) {
        this.mvc = mvc;
        this.session = session;
    }

    public ResultActions get(String path, Object... vars) throws Exception {
        return mvc.perform(authorized(HttpMethod.GET, path, vars));
    }

    public ResultActions delete(String path, Object... vars) throws Exception {
        return mvc.perform(authorized(HttpMethod.DELETE, path, vars));
    }

    public ResultActions deleteJson(String path, String json, Object... vars) throws Exception {
        return mvc.perform(withJson(authorized(HttpMethod.DELETE, path, vars), json));
    }

    public ResultActions post(String path, String json, Object... vars) throws Exception {
        return mvc.perform(withJson(authorized(HttpMethod.POST, path, vars), json));
    }

    public ResultActions put(String path, String json, Object... vars) throws Exception {
        return mvc.perform(withJson(authorized(HttpMethod.PUT, path, vars), json));
    }

    public ResultActions patch(String path, String json, Object... vars) throws Exception {
        return mvc.perform(withJson(authorized(HttpMethod.PATCH, path, vars), json));
    }

    /** Lê um valor (JsonPath) do corpo da resposta. */
    public static <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    private MockHttpServletRequestBuilder authorized(HttpMethod method, String path, Object... vars) {
        return request(method, path, vars).header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    private static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, String json) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json == null ? "{}" : json);
    }
}
