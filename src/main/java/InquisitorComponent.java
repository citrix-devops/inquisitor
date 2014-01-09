package com.citrix.jira;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

import com.atlassian.annotations.PublicApi;


public interface InquisitorComponent
{
    Response echo();
    Response issues(@PathParam("key") String key, @QueryParam("issuetype") String issuetype, @QueryParam("jql") String jql);
    Response tctree(@QueryParam("key") String key, @QueryParam("children") String children, @QueryParam("toptickets") String topTickets);
}
