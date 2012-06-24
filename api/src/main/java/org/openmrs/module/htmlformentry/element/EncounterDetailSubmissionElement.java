package org.openmrs.module.htmlformentry.element;

import java.lang.reflect.Method;
import java.util.*;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import ca.uhn.hl7v2.model.v24.segment.AUT;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.velocity.exception.ParseErrorException;
import org.hibernate.engine.*;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntryContext.Mode;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionError;
import org.openmrs.module.htmlformentry.HtmlFormEntryService;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.comparator.OptionComparator;
import org.openmrs.module.htmlformentry.widget.*;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.util.StringUtils;

/**
 * Holds the widgets used to represent an Encounter details, and serves as both the
 * HtmlGeneratorElement and the FormSubmissionControllerAction for Encounter details.
 */
public class EncounterDetailSubmissionElement implements HtmlGeneratorElement, FormSubmissionControllerAction {

    private String id;

    private DateWidget dateWidget;

    private ErrorWidget dateErrorWidget;

    private TimeWidget timeWidget;

    private ErrorWidget timeErrorWidget;

    private SingleOptionWidget providerWidget;

    private ErrorWidget providerErrorWidget;

    private SingleOptionWidget locationWidget;

    private ErrorWidget locationErrorWidget;

    private CheckboxWidget voidWidget;

    private ErrorWidget voidErrorWidget;

    /**
     * Construct a new EncounterDetailSubmissionElement
     *
     * @param context
     * @param parameters
     */
    public EncounterDetailSubmissionElement(FormEntryContext context, Map<String, Object> parameters) {

        // Register Date and Time widgets, if appropriate
        if (Boolean.TRUE.equals(parameters.get("date"))) {

            dateWidget = new DateWidget();
            dateErrorWidget = new ErrorWidget();

            if (context.getExistingEncounter() != null) {
                dateWidget.setInitialValue(context.getExistingEncounter().getEncounterDatetime());
            } else if (parameters.get("defaultDate") != null) {
                dateWidget.setInitialValue(parameters.get("defaultDate"));
            }

            if (parameters.get("disallowMultipleEncountersOnDate") != null
                    && StringUtils.hasText((String) parameters.get("disallowMultipleEncountersOnDate"))) {
                dateWidget.setOnChangeFunction("existingEncounterOnDate(this, '"
                        + parameters.get("disallowMultipleEncountersOnDate") + "') ");
            }

            if ("true".equals(parameters.get("showTime"))) {
                timeWidget = new TimeWidget();
                timeErrorWidget = new ErrorWidget();
                if (context.getExistingEncounter() != null) {
                    timeWidget.setInitialValue(context.getExistingEncounter().getEncounterDatetime());
                } else if (parameters.get("defaultDate") != null) {
                    timeWidget.setInitialValue(parameters.get("defaultDate"));
                }
                context.registerWidget(timeWidget);
                context.registerErrorWidget(timeWidget, timeErrorWidget);
            }
            context.registerWidget(dateWidget);
            context.registerErrorWidget(dateWidget, dateErrorWidget);
        }

        // Register Provider widgets, if appropriate
        if (Boolean.TRUE.equals(parameters.get("provider"))) {

            if ("autocomplete".equals(parameters.get("type"))) {
                providerWidget = new AutocompleteWidget();
            }else{
                providerWidget = new DropdownWidget();
            }
            providerErrorWidget = new ErrorWidget();

            List<Option> providerOptions = new ArrayList<Option>();
            // If specific persons are specified, display only those persons in order
            String personsParam = (String) parameters.get("persons");
            if (personsParam != null) {
                for (String s : personsParam.split(",")) {
                    Person p = HtmlFormEntryUtil.getPerson(s);
                    if (p == null) {
                        throw new RuntimeException("Cannot find Person: " + s);
                    }
                    String label = p.getPersonName().getFullName();
                    providerOptions.add(new Option(label, p.getId().toString(), false));
                }
                removeNonProviders(providerOptions);
            }

            // Only if specific person ids are not passed in do we get by user Role
            if (providerOptions.isEmpty()) {

                List<PersonStub> users = new ArrayList<PersonStub>();
                List<Option> providerUsers = new ArrayList<Option>();

                // If the "role" attribute is passed in, limit to users with this role
                if (parameters.get("role") != null) {
                    Role role = Context.getUserService().getRole((String) parameters.get("role"));
                    if (role == null) {
                        throw new RuntimeException("Cannot find role: " + parameters.get("role"));
                    } else {
                        users = Context.getService(HtmlFormEntryService.class).getUsersAsPersonStubs(role.getRole());
                    }
                }

                // Otherwise, use default options appropriate to the underlying OpenMRS version
                else {
                    if (openmrsVersionDoesNotSupportProviders()) {
                        // limit to users with the default OpenMRS PROVIDER role,
                        String defaultRole = OpenmrsConstants.PROVIDER_ROLE;
                        Role role = Context.getUserService().getRole(defaultRole);
                        if (role != null) {
                            users = Context.getService(HtmlFormEntryService.class).getUsersAsPersonStubs(role.getRole());
                        }
                        // If this role isn't used, default to all Users
                        if (users.isEmpty()) {
                            users = Context.getService(HtmlFormEntryService.class).getUsersAsPersonStubs(null);
                        }
                    } else {
                        // in OpenMRS 1.9+, get all suitable providers
                        users = getAllProvidersThatArePersonsAsPersonStubs();
                    }
                }

                for (PersonStub personStub : users) {

                    Option option = new Option(personStub.toString(), personStub.getId().toString(), false);
                    providerUsers.add(option);
                }
                providerOptions.addAll(providerUsers);

            }

            // Set default values as appropriate
            Person defaultProvider = null;
            Option defProviderOption;
            if (context.getExistingEncounter() != null) {
                defaultProvider = context.getExistingEncounter().getProvider();
                // this is done to avoid default provider being added twice due to that it can be added from the
                // users = getAllProvidersThatArePersonsAsPersonStubs(); section with selected="false", therefore this can't be caught when
                // searching whether the options list contains the 'defaultProvider'
              boolean defaultOptionPresent = false;
              for(Option option: providerOptions){
                  if(option.getValue().equals(defaultProvider.getId().toString())){
                      defaultOptionPresent = true;
                      providerOptions.remove(option);
                      break;
                  }
              }
              if(defaultOptionPresent)  {
                  defProviderOption
                     = new Option(defaultProvider.getPersonName().getFullName(), defaultProvider.getId().toString(), true);
                   providerOptions.add(defProviderOption);
              }

            } else {
                String defParam = (String) parameters.get("default");
                if (StringUtils.hasText(defParam)) {
                    if ("currentuser".equalsIgnoreCase(defParam)) {
                        defaultProvider = Context.getAuthenticatedUser().getPerson();
                    } else {
                        defaultProvider = HtmlFormEntryUtil.getPerson(defParam);
                    }
                    if (defaultProvider == null) {
                        throw new IllegalArgumentException("Invalid default provider specified for encounter: " + defParam);
                    } else {
                        defProviderOption
                                = new Option(defaultProvider.getPersonName().getFullName(), defaultProvider.getId().toString(), true);
                        for (Option option : providerOptions) {
                            if (option.getValue().equals(defProviderOption.getValue())) {
                                providerOptions.remove(option);
                                break;
                            }
                        }
                        providerOptions.add(defProviderOption);
                    }

                }
            }
            if (defaultProvider != null) {
                providerWidget.setInitialValue(new PersonStub(defaultProvider));
            }
            Collections.sort(providerOptions,new OptionComparator());

            if (("autocomplete").equals(parameters.get("type"))) {
                providerWidget.addOption(new Option());
                if (!providerOptions.isEmpty()) {
                    providerWidget.setOptions(providerOptions);
                }

            } else {
                // if initialValueIsSet=false, no initial/default provider, hence this shows the 'select input' field as first option
                boolean initialValueIsSet = !(providerWidget.getInitialValue() == null);
                providerWidget.addOption(new Option
                        (Context.getMessageSourceService().getMessage("htmlformentry.chooseAProvider"), "", !initialValueIsSet)); // if no initial or default value

                if (!providerOptions.isEmpty()) {
                    for (Option option : providerOptions) {
                        providerWidget.addOption(option);
                    }

                }
            }
            context.registerWidget(providerWidget);
            context.registerErrorWidget(providerWidget, providerErrorWidget);
        }

        // Register Location widgets, if appropriate
        if (Boolean.TRUE.equals(parameters.get("location"))) {

                locationErrorWidget = new ErrorWidget();
            List<Location> locations = new ArrayList<Location>();
            List<Option> locationOptions = new ArrayList<Option>();

            if ("autocomplete".equals(parameters.get("type"))) {
                locationWidget = new AutocompleteWidget();
            } else {
                locationWidget = new DropdownWidget();
            }

            // If the "order" attribute is passed in, limit to the specified locations in order
            if (parameters.get("order") != null) {

                String[] temp = ((String) parameters.get("order")).split(",");
                for (String s : temp) {
                    Location loc = HtmlFormEntryUtil.getLocation(s);
                    if (loc == null) {
                        throw new RuntimeException("Cannot find location: " + loc);
                    }
                    locations.add(loc);
                }

            }

            // Set default values
            Location defaultLocation = null;
            if (context.getExistingEncounter() != null) {
                defaultLocation = context.getExistingEncounter().getLocation();
            } else {
                String defaultLocId = (String) parameters.get("default");
                if (StringUtils.hasText(defaultLocId)) {
                    defaultLocation = HtmlFormEntryUtil.getLocation(defaultLocId);
                }
            }
            defaultLocation = defaultLocation == null ? context.getDefaultLocation() : defaultLocation;
            locationWidget.setInitialValue(defaultLocation);

            if (!locations.isEmpty()) {
                for (Location location : locations) {
                    String label = location.getName();
                    Option option = new Option(label, location.getId().toString(), location.equals(defaultLocation));
                    locationOptions.add(option);
                }
            } else {
                locations = Context.getLocationService().getAllLocations();
                for (Location location : locations) {
                    String label = location.getName();
                    Option option = new Option(label, location.getId().toString(), location.equals(defaultLocation));
                    locationOptions.add(option);
                }
                Collections.sort(locationOptions, new OptionComparator());
            }

            if ("autocomplete".equals(parameters.get("type"))) {
                locationWidget.addOption(new Option());
                if (!locationOptions.isEmpty()) {
                    locationWidget.setOptions(locationOptions);
                }
            } else {
                boolean initialValueIsSet = !(locationWidget.getInitialValue() == null);
                locationWidget.addOption(new Option
                        (Context.getMessageSourceService().getMessage("htmlformentry.chooseALocation"), "", !initialValueIsSet));
                if (!locationOptions.isEmpty()) {
                    for (Option option : locationOptions)
                        locationWidget.addOption(option);
                }
            }
            context.registerWidget(locationWidget);
            context.registerErrorWidget(locationWidget, locationErrorWidget);
        }


        if (Boolean.TRUE.equals(parameters.get("showVoidEncounter")) && context.getMode() == Mode.EDIT) { //only show void option if the encounter already exists.  And VIEW implies not voided.
            voidWidget = new CheckboxWidget();
            voidWidget.setLabel(" " + Context.getMessageSourceService().getMessage("general.voided"));
            voidErrorWidget = new ErrorWidget();
            if (context.getExistingEncounter() != null && context.getExistingEncounter().isVoided().equals(true))
                voidWidget.setInitialValue("true");
            context.registerWidget(voidWidget);
            context.registerErrorWidget(voidWidget, voidErrorWidget);
        }

        // set the id, if it has been specified
        if (parameters.get("id") != null) {
            id = (String) parameters.get("id");
        }

    }

    /**
     * This method exists to allow us to quickly support providers as introduce in OpenMRS 1.9.x,
     * without having to branch the module. We should remove this method when do a proper
     * implementation.
     *
     * @return
     */
    private boolean openmrsVersionDoesNotSupportProviders() {
        return OpenmrsConstants.OPENMRS_VERSION_SHORT.startsWith("1.6")
                || OpenmrsConstants.OPENMRS_VERSION_SHORT.startsWith("1.7")
                || OpenmrsConstants.OPENMRS_VERSION_SHORT.startsWith("1.8");
    }

    /**
     * This method exists to allow us to quickly support providers as introduce in OpenMRS 1.9.x,
     * without having to branch the module. We should remove this method when do a proper
     * implementation.
     *
     * @param persons
     */
    private void removeNonProviders(List<Option> persons) {
        if (openmrsVersionDoesNotSupportProviders())
            return;
        Set<Integer> legalPersonIds = getAllProviderPersonIds();
        for (Iterator<Option> i = persons.iterator(); i.hasNext(); ) {
            Option candidate = i.next();
            if (!legalPersonIds.contains(Integer.parseInt(candidate.getValue())))
                i.remove();
        }
    }

    /**
     * This method exists to allow us to quickly support providers as introduce in OpenMRS 1.9.x,
     * without having to branch the module. We should remove this method when do a proper
     * implementation.
     *
     * @return all providers that are attached to persons
     */
    private List<Object> getAllProvidersThatArePersons() {
        if (openmrsVersionDoesNotSupportProviders())
            throw new RuntimeException(
                    "Programming error in HTML Form Entry module. This method should not be called before OpenMRS 1.9.");
        try {
            Object providerService = Context.getService(Context.loadClass("org.openmrs.api.ProviderService"));
            Method getProvidersMethod = providerService.getClass().getMethod("getAllProviders");
            @SuppressWarnings("rawtypes")
            List allProviders = (List) getProvidersMethod.invoke(providerService);
            List<Object> ret = new ArrayList<Object>();
            for (Object provider : allProviders) {
                Person person = (Person) PropertyUtils.getProperty(provider, "person");
                if (person != null)
                    ret.add(provider);
            }
            return ret;
        } catch (Exception ex) {
            throw new RuntimeException("Programming error in HTML Form Entry module. This method should be safe!", ex);
        }
    }

    /**
     * This method exists to allow us to quickly support providers as introduce in OpenMRS 1.9.x,
     * without having to branch the module. We should remove this method when do a proper
     * implementation.
     *
     * @return person stubs for all providers that are attached to persons
     */
    private List<PersonStub> getAllProvidersThatArePersonsAsPersonStubs() {
        try {
            List<PersonStub> ret = new ArrayList<PersonStub>();
            for (Object provider : getAllProvidersThatArePersons()) {
                Person person = (Person) PropertyUtils.getProperty(provider, "person");
                ret.add(new PersonStub(person));
            }
            return ret;
        } catch (Exception ex) {
            throw new RuntimeException("Programming error in HTML Form Entry module. This method should be safe!", ex);
        }
    }

    /**
     * This method exists to allow us to quickly support providers as introduce in OpenMRS 1.9.x,
     * without having to branch the module. We should remove this method when do a proper
     * implementation.
     *
     * @return personIds of all providers that are attached to persons
     */
    private Set<Integer> getAllProviderPersonIds() {
        try {
            Set<Integer> ret = new HashSet<Integer>();
            for (Object candidate : getAllProvidersThatArePersons()) {
                Person person = (Person) PropertyUtils.getProperty(candidate, "person");
                if (person != null)
                    ret.add(person.getPersonId());
            }
            return ret;
        } catch (Exception ex) {
            throw new RuntimeException("Programming error in HTML Form Entry module. This method should be safe!", ex);
        }
    }

    /**
     * @see HtmlGeneratorElement#generateHtml(FormEntryContext)
     */
    @Override
    public String generateHtml(FormEntryContext context) {
        StringBuilder ret = new StringBuilder();

        // if an id has been specified, wrap the whole encounter element in a span tag so that we access property values via javascript
        // also register property accessors for all the widgets
        if (id != null) {
            ret.append("<span id='" + id + "'>");

            // note that if this element ever handles multiple widgets, the names of the provider and location accessors will need unique names
            if (dateWidget != null) {
                context.registerPropertyAccessorInfo(id + ".value", context.getFieldNameIfRegistered(dateWidget),
                        "dateFieldGetterFunction", null, "dateSetterFunction");
                context.registerPropertyAccessorInfo(id + ".error", context.getFieldNameIfRegistered(dateErrorWidget), null,
                        null, null);
            } else if (providerWidget != null) {
                context.registerPropertyAccessorInfo(id + ".value", context.getFieldNameIfRegistered(providerWidget), null,
                        null, null);
                context.registerPropertyAccessorInfo(id + ".error", context.getFieldNameIfRegistered(providerErrorWidget),
                        null, null, null);
            } else if (locationWidget != null) {
                context.registerPropertyAccessorInfo(id + ".value", context.getFieldNameIfRegistered(locationWidget), null,
                        null, null);
                context.registerPropertyAccessorInfo(id + ".error", context.getFieldNameIfRegistered(locationErrorWidget),
                        null, null, null);
            }
        }

        if (dateWidget != null) {
            ret.append(dateWidget.generateHtml(context));
            if (context.getMode() != Mode.VIEW)
                ret.append(dateErrorWidget.generateHtml(context));
        }
        if (timeWidget != null) {
            ret.append("&#160;");
            ret.append(timeWidget.generateHtml(context));
            if (context.getMode() != Mode.VIEW)
                ret.append(timeErrorWidget.generateHtml(context));
        }
        if (providerWidget != null) {
            ret.append(providerWidget.generateHtml(context));
            if (context.getMode() != Mode.VIEW)
                ret.append(providerErrorWidget.generateHtml(context));
        }
        if (locationWidget != null) {
            ret.append(locationWidget.generateHtml(context));
            if (context.getMode() != Mode.VIEW)
                ret.append(locationErrorWidget.generateHtml(context));
        }
        if (voidWidget != null) {
            if (context.getMode() == Mode.EDIT) //only show void option if the encounter already exists.
                ret.append(voidWidget.generateHtml(context));
        }

        // close out the span if we have an id tag
        if (id != null) {
            ret.append("</span>");
        }

        return ret.toString();
    }

    /**
     * @see FormSubmissionControllerAction#validateSubmission(FormEntryContext, HttpServletRequest)
     */
    @Override
    public Collection<FormSubmissionError> validateSubmission(FormEntryContext context, HttpServletRequest submission) {
        List<FormSubmissionError> ret = new ArrayList<FormSubmissionError>();

        try {
            if (dateWidget != null) {
                Date date = (Date) dateWidget.getValue(context, submission);
                if (timeWidget != null) {
                    Date time = (Date) timeWidget.getValue(context, submission);
                    date = HtmlFormEntryUtil.combineDateAndTime(date, time);
                }
                if (date == null)
                    throw new Exception("htmlformentry.error.required");
                if (OpenmrsUtil.compare((Date) date, new Date()) > 0)
                    throw new Exception("htmlformentry.error.cannotBeInFuture");
            }
        } catch (Exception ex) {
            ret.add(new FormSubmissionError(context.getFieldName(dateErrorWidget), Context.getMessageSourceService()
                    .getMessage(ex.getMessage())));
        }

        try {
            if (providerWidget != null) {
                Object value = providerWidget.getValue(context, submission);
                Person provider = (Person) convertValueToProvider(value);
                if (provider == null)
                    throw new Exception("required");
            }
        } catch (Exception ex) {
            ret.add(new FormSubmissionError(context.getFieldName(providerErrorWidget), Context.getMessageSourceService()
                    .getMessage(ex.getMessage())));
        }

        try {
            if (locationWidget != null) {
                Object value = locationWidget.getValue(context, submission);
                Location location = (Location) HtmlFormEntryUtil.convertToType(value.toString().trim(), Location.class);
                if (location == null)
                    throw new Exception("required");
            }
        } catch (Exception ex) {
            ret.add(new FormSubmissionError(context.getFieldName(locationErrorWidget), Context.getMessageSourceService()
                    .getMessage(ex.getMessage())));
        }
        return ret;
    }


    /**
     * Gets provider id and obtains the Provider from it
     *
     * @param value - provider id
     * @return the Provider object of corresponding id
     */
    private Object convertValueToProvider(Object value) {
        String val = (String) value;
        if (StringUtils.hasText(val)) {
            return HtmlFormEntryUtil.convertToType(val.trim(), Person.class);
        }
        return null;
    }

    /**
     * @see FormSubmissionControllerAction#handleSubmission(FormEntrySession, HttpServletRequest)
     */
    @Override
    public void handleSubmission(FormEntrySession session, HttpServletRequest submission) {
        if (dateWidget != null) {
            Date date = (Date) dateWidget.getValue(session.getContext(), submission);
            if (session.getSubmissionActions().getCurrentEncounter().getEncounterDatetime() != null
                    && !session.getSubmissionActions().getCurrentEncounter().getEncounterDatetime().equals(date)) {
                session.getContext().setPreviousEncounterDate(
                        new Date(session.getSubmissionActions().getCurrentEncounter().getEncounterDatetime().getTime()));
            }
            session.getSubmissionActions().getCurrentEncounter().setEncounterDatetime(date);
        }
        if (timeWidget != null) {
            Date time = (Date) timeWidget.getValue(session.getContext(), submission);
            Encounter e = session.getSubmissionActions().getCurrentEncounter();
            Date dateAndTime = HtmlFormEntryUtil.combineDateAndTime(e.getEncounterDatetime(), time);
            e.setEncounterDatetime(dateAndTime);
        }
        if (providerWidget != null) {
            Object value = providerWidget.getValue(session.getContext(), submission);
            Person person = (Person) HtmlFormEntryUtil.convertToType(value.toString().trim(), Person.class);
            session.getSubmissionActions().getCurrentEncounter().setProvider(person);
        }
        if (locationWidget != null) {
            Object value = locationWidget.getValue(session.getContext(), submission);
            Location location = (Location) HtmlFormEntryUtil.convertToType(value.toString().trim(), Location.class);
            session.getSubmissionActions().getCurrentEncounter().setLocation(location);
        }
        if (voidWidget != null) {
            if ("true".equals(voidWidget.getValue(session.getContext(), submission))) {
                session.setVoidEncounter(true);
            } else if ("false".equals(voidWidget.getValue(session.getContext(), submission))) {
                //nothing..  the session.voidEncounter property will be false, and the encounter will be un-voided if already voided
                //otherwise, nothing will happen.  99% of the time the encounter won't be voided to begin with.
            }
        }
    }
}
