# Inquisitor Jira Plugin

Plugin for JIRA that provides additional JIRA REST APIs that are blazing fast. The plugin is at least one order of magnitude faster than JIRA out of the box REST API, allowing you to export ~30000 issues in less than 10 seconds.

After installation, use the Atlassian REST API Browser to view the additional APIs. Untick the "Show only public APIs" option and enter "inquisitor" in the search box.

## API

Three additional APIs are provided:
  1. echo - tests the plugin is working
  2. issues - returns a list of jira issues, with a limited set of field values:
    * key
    * issuetype
    * summary
    * created
    * components
    * reporter
    * reporterName
    * assignee
    * assigneeName
    * resolution
  3. issuetree - returns a jira issue and any associated child jira by following any "contains" links

## Examples

In the following examples replace USERNAME and PASSWORD with your credentials and replace "jira.com" with the URL for your Jira Server.

To test the plugin works and returns an echo string:

curl -D- -u USERNAME:PASSWORD -X GET -H "Content-Type: application/json" https://jira.com/rest/inquisitor/1.0/echo

To retrieve a list of jira, referencing the project with ID 10000 and retrieving a maximum of 100 results:

curl -D- -u USERNAME:PASSWORD -X GET -H "Content-Type: application/json" https://jira.com/rest/inquisitor/1.0/issues/10000?maxResults=100

Get details of a single JIRA and children (following the "contains" links):

curl -D- -u USERNAME:PASSWORD -X GET -H "Content-Type: application/json" https://jira.com/rest/inquisitor/1.0/issuetree/JRA-1234?depth=2

