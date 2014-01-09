package com.citrix.jira;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.annotations.PublicApi;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.DelegatingApplicationUser;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.*;
import com.atlassian.jira.issue.comparator.IssueKeyComparator;
import com.atlassian.jira.issue.context.IssueContext;
import com.atlassian.jira.issue.context.IssueContextImpl;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.link.IssueLink;
import com.atlassian.jira.issue.link.IssueLinkManager;

import java.util.*;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.JSONArray;


@Path("/")
@AnonymousAllowed
@Consumes({MediaType.APPLICATION_JSON}) // MediaType.APPLICATION_XML
@Produces({MediaType.APPLICATION_JSON})
@PublicApi
public class InquisitorComponentImpl implements InquisitorComponent
{
    private final ApplicationProperties applicationProperties;

    @SuppressWarnings("unchecked")
    private JSONObject handleTCTreeInner(Issue parent, boolean doChildren, IssueManager issueMgr, IssueLinkManager linkMgr) throws Exception {
        List<Issue> issues = new ArrayList<Issue>();
        JSONObject ret = new JSONObject();
        ret.put("key", parent.getKey());
        ret.put("title", parent.getSummary());
        ret.put("type", parent.getIssueTypeObject().getName());
        Collection<IssueLink> outlinks = linkMgr.getOutwardLinks(parent.getId());
        for (IssueLink il : outlinks) {
            if (il.getIssueLinkType().getOutward().equals("contains"))
                issues.add(il.getDestinationObject());
        }
        if (issues.size() > 0) {
            if (doChildren) {
                JSONArray children = new JSONArray();
                for (Issue i: issues) {
                    children.add(handleTCTreeInner(i, true, issueMgr, linkMgr));
                }
                ret.put("children", children);
            }
            ret.put("hasChildren", true);
        }
        else {
            ret.put("hasChildren", false);
        }
        return ret;
    }


    public InquisitorComponentImpl(ApplicationProperties applicationProperties)
    {
        this.applicationProperties = applicationProperties;
    }
    @GET
    @AnonymousAllowed
    @Path("echo")
    @PublicApi
    public Response echo() {
        return Response.ok("{ \"echo\": \"echo\" }").build();
    }

    @GET
    @AnonymousAllowed
    @Path("tctree")
    @PublicApi
    @SuppressWarnings("unchecked")
    public Response tctree(@QueryParam("key") String key, @QueryParam("children") String children, @QueryParam("toptickets") String topTickets) {
        try {
            JSONArray list = new JSONArray();
            boolean doChildren = (children != null);
            IssueManager issueMgr;
            issueMgr = ComponentAccessor.getIssueManager();
            IssueLinkManager linkMgr;
            linkMgr = ComponentAccessor.getIssueLinkManager();
            if (key == null) {
                String[] topString = topTickets.split(",");
                List<Issue> tops = new ArrayList<Issue>();
                for (String topTC : topString)
                    tops.add(issueMgr.getIssueObject(topTC));

                for (Issue i : tops) {
                    list.add(handleTCTreeInner(i, doChildren, issueMgr, linkMgr));
                }
            }
            else {
                Issue i = issueMgr.getIssueObject(key);
                Collection<IssueLink> outlinks = linkMgr.getOutwardLinks(i.getId());
                for (IssueLink il : outlinks) {
                    if (il.getIssueLinkType().getOutward().equals("contains"))
                        list.add(handleTCTreeInner(il.getDestinationObject(), doChildren, issueMgr, linkMgr));
                }
            }
            return Response.ok(list).build();
        }
        catch (Exception e){
            return Response.serverError().entity(e).build();
        }
    }


    @GET
    @AnonymousAllowed
    @Path("project/{key}")
    @PublicApi
    @SuppressWarnings("unchecked")
    public Response issues(@PathParam("key") String key, @QueryParam("issuetype") String issuetype, @QueryParam("jql") String jql) {
        try {
            JSONArray list = new JSONArray();

            JiraAuthenticationContext authContext = ComponentAccessor.getJiraAuthenticationContext();
            User user;
            if(!authContext.isLoggedInUser()) {
                // user = ComponentAccessor.getUserManager().getUser("sorins");
                throw new Exception("Cannot run this as anonymous.");
            } else {
                user = authContext.getUser().getDirectoryUser();
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
                    parseResult.getQuery(), PagerFilter.getUnlimitedFilter());

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
