package com.citrix.jira;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

import com.atlassian.annotations.PublicApi;


public interface InquisitorComponent
{
    Response echo();
    Response issuetree(@PathParam("rootProjectOrKey") String key, @DefaultValue("") @QueryParam("depth") Integer depth);
    Response issues(@PathParam("projectIdOrKey") String key,
                           @QueryParam("issueType") String issuetype,
                           @QueryParam("jql") String jql,
                           @DefaultValue("0") @QueryParam("startAt") int startAt,
                           @DefaultValue("25000") @QueryParam("maxResults") int maxResults);
}