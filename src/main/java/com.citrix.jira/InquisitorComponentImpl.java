package com.citrix.jira;
import com.atlassian.annotations.PublicApi;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.link.IssueLink;
import com.atlassian.jira.issue.link.IssueLinkManager;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.sal.api.ApplicationProperties;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.List;
/*
// Customized {@code ContextResolver} implementation to pass ObjectMapper to use
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JacksonContextResolver implements ContextResolver<ObjectMapper> {
    private ObjectMapper objectMapper;

    public JacksonContextResolver() throws Exception {
        this.objectMapper = new ObjectMapper().configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    public ObjectMapper getContext(Class<?> objectType) {
        return objectMapper;
    }
}
*/

@Path("/")
//@AnonymousAllowed
@Consumes({MediaType.APPLICATION_JSON}) // MediaType.APPLICATION_XML
@Produces({MediaType.APPLICATION_JSON})
@PublicApi
public class InquisitorComponentImpl implements InquisitorComponent
{
    private final ApplicationProperties applicationProperties;
    private static final Logger log = Logger.getLogger(InquisitorComponent.class);
    private static IssueManager issueMgr = ComponentAccessor.getIssueManager();
    private static IssueLinkManager linkMgr = ComponentAccessor.getIssueLinkManager();

    @SuppressWarnings("unchecked")
    private JSONObject handleIssueTreeInner(Issue parent, int depth) throws Exception {

        JSONObject ret = new JSONObject();
        if(parent == null) {
            ret.put("error", true);
            return ret;
         }
        ret.put("key", parent.getKey());
        ret.put("title", parent.getSummary());
        ret.put("type", parent.getIssueTypeObject().getName());
        Collection<IssueLink> outlinks = linkMgr.getOutwardLinks(parent.getId());
        JSONArray children = new JSONArray();

        for (IssueLink il : outlinks) {
            if (il.getIssueLinkType().getOutward().equals("contains"))
            {
                if (depth != 0)
                    children.add(handleIssueTreeInner(il.getDestinationObject(), depth-1));
                else
                    children.add(il.getDestinationObject().getKey());
            }
        }
        if (!children.isEmpty()) {
            ret.put("children", children);
        }
        //else {
        //    ret.put("hasChildren", false);
        //}
        return ret;
    }


    public InquisitorComponentImpl(ApplicationProperties applicationProperties)
    {
        this.applicationProperties = applicationProperties;
    }
    @GET
    //@AnonymousAllowed
    @Path("echo")
    @PublicApi
    public Response echo() {
        return Response.ok("{ \"echo\": \"echo\" }").build();
    }

    @GET
    //@AnonymousAllowed
    @Path("issuetree/{rootProjectOrKey}")
    @PublicApi
    @SuppressWarnings("unchecked")
    public Response issuetree(@PathParam("rootProjectOrKey") String key, @DefaultValue("0") @QueryParam("depth") Integer depth) {

        try {
            JSONArray list = new JSONArray();

            if (key == null || key.isEmpty()) {
                return Response.status(400).entity("{ \"error\": \"Parameters should be specified.\" }").build();
            }
            String[] keys = key.split(",");
            for (String k : keys)
                if (k.contains("-")) {
                    Issue i = issueMgr.getIssueObject(k);
                    if(i != null)
                        list.add(handleIssueTreeInner(i, depth));
                }
            if (list.isEmpty()) {
                return Response.ok(Response.status(Response.Status.NOT_FOUND)).build();
            }
            return Response.ok(list).build();
        }
        catch (Exception e){
            log.error("Exception: ", e);
            return Response.serverError().entity(e).build();
        }
    }


    @GET
    @AnonymousAllowed
    @Path("issues/{projectIdOrKey}")
    @PublicApi
    @SuppressWarnings("unchecked")
    public Response issues(@PathParam("projectIdOrKey") String key,
                           @QueryParam("issueType") String issuetype,
                           @QueryParam("jql") String jql,
                           @DefaultValue("0") @QueryParam("startAt") int startAt,
                           @DefaultValue("25000") @QueryParam("maxResults") int maxResults) {
        try {
            JSONArray list = new JSONArray();

            JiraAuthenticationContext authContext = ComponentAccessor.getJiraAuthenticationContext();


            User user = authContext.getUser().getDirectoryUser();

            //final UserWithKey loggedInUser = UserCompatibilityHelper.convertUserObject(authContext.getUser());

            //ApplicationUser au = (ApplicationUser) UserCompatibilityHelper.convertUserObject(remoteUser);
            //ßreturn doGetActions(issue, toDirectoryUser(au), 20);



            if(!authContext.isLoggedInUser()) {
                throw new Exception("Cannot run this as anonymous.");
            } else {
                //user = authContext.getUser().getDirectoryUser();
                //user = loggedInUser.getUser(); // au.getDirectoryUser();
            }

            if (jql == null) {
                jql = String.format("project = %s", key);
                if (issuetype != null) {
                    jql = String.format("project = %s AND issuetype = %s", key, issuetype);
                }
            }
            final SearchService.ParseResult parseResult =
                    ComponentAccessor.getComponentOfType(SearchService.class)
                    .parseQuery(user, jql);

            SearchResults results = ComponentAccessor.getComponentOfType(SearchService.class).search(user,
                    parseResult.getQuery(), PagerFilter.newPageAlignedFilter(startAt, maxResults));

            List<Issue> issues = results.getIssues();

            for (Issue t: issues) {
                JSONObject obj = new JSONObject();
                obj.put("key", t.getKey());
                obj.put("summary", t.getSummary()); // title changes to summary
                obj.put("issuetype", t.getIssueTypeObject().getName());
                obj.put("created", t.getCreated().getTime());
                try {
                    obj.put("assigneeName", t.getAssigneeUser().getDisplayName());
                }
                catch (Exception e) {
                    obj.put("assigneeName", null);
                }
                obj.put("assignee", t.getAssigneeId());
                try {
                    obj.put("reporterName", t.getReporterUser().getDisplayName());
                }
                catch (Exception e) {
                    obj.put("reporterName", null);
                }
                obj.put("reporter", t.getReporterId());
                if (t.getResolutionObject() != null) {
                    obj.put("resolution", t.getResolutionObject().getName());
                }
                else {
                    obj.put("resolution", null);
                }

                Collection<ProjectComponent> comps = t.getComponentObjects();
                JSONArray cs = new JSONArray();
                for (ProjectComponent c: comps) {
                    JSONObject comp = new JSONObject();
                    comp.put("name", c.getName());
                    comp.put("id", c.getId());
                    comp.put("lead", c.getLead());
                    cs.add(comp);
                }
                obj.put("components", cs);

                list.add(obj);
            }
            return Response.ok(list).build();

        }
        catch (Exception e){
            return Response.serverError().entity(e).build();
        }
    }

}
