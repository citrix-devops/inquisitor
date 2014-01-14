package com.citrix.jira;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

import com.atlassian.annotations.PublicApi;


public interface InquisitorComponent
{
    Response echo();
    Response issues(@PathParam("projectIdOrKey") String key, @QueryParam("issueType") String issueType, @QueryParam("jql") String jql);
    Response issuetree(@PathParam("rootProjectOrKey") String key, @DefaultValue("") @QueryParam("depth") Integer depth);

}
