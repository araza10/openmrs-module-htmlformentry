package org.openmrs.module.htmlformentry.widget;


import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.springframework.web.util.HtmlUtils;

import java.lang.String;
import java.util.Iterator;

/**
 *   A single option auto complete widget which provides auto complete suggestions using a list of  predefined options
 */

public class AutocompleteWidget extends  SingleOptionWidget{

    private Option initialOption;
    private String optionNames;
    private String optionValues;

    public AutocompleteWidget() {
    }


    @Override
    public String generateHtml(FormEntryContext context) {

         if (context.getMode() == FormEntryContext.Mode.VIEW) {
            String toPrint = "";
            if (getInitialValue() != null) {
                // lookup the label for the selected value
                boolean found = false;
                for (Option o : getOptions()) {
                    if (getInitialValue().equals(o.getLabel())) {
                        toPrint = o.getLabel();
                        found = true;
                        break;
                    }
                }
                if (!found){
                    toPrint = getInitialValue();
                }
                return WidgetFactory.displayValue(toPrint);
            } else {
                toPrint = "____";
                return WidgetFactory.displayEmptyValue(toPrint);
            }
        }else {
            StringBuilder sb = new StringBuilder();
            String id = context.getFieldName(this);

            if(!getOptions().isEmpty()){
            StringBuilder nameSb = new StringBuilder();
            StringBuilder valueSb = new StringBuilder();

            for (Iterator<Option> it = getOptions().iterator(); it.hasNext();) {
                String originaloption = it.next().getLabel();
                originaloption = originaloption.replace("\\", "\\" +"\\" );
                originaloption = originaloption.replace("'", "\\" +"'");
                originaloption = originaloption.replace("\"", "\\" +"'" );

				nameSb.append(originaloption);
				if (it.hasNext()){
                   nameSb.append(",");
                }
			}

            for (Iterator<Option> it = getOptions().iterator(); it.hasNext();) {
                valueSb.append(it.next().getValue());
				if (it.hasNext()){
                   valueSb.append(",");
                }
			}
            this.optionNames = nameSb.toString();
            this.optionValues = valueSb.toString();
                System.out.println("option labels:  " + nameSb.toString());
                System.out.println("option values:  " + valueSb.toString());
        }

            // set the previously given option into widget, when editing the form, else initialOption is null
            if (context.getMode() == FormEntryContext.Mode.EDIT) {
             for (Option o : getOptions()) {
                    if (getInitialValue().equals(o.getLabel())) {
                        initialOption = new Option(o.getLabel(),o.getValue(),false);
                    }
                }
            }

            sb.append("<input type=\"text\" id=\"" + id + "\" value=\""
			        + ((initialOption != null) ? HtmlUtils.htmlEscape(initialOption.getLabel()) : "")
			        + "\" onblur=\"onblurOptionAutocomplete(this,'" +this.optionNames +"','" + this.optionValues
                    + "')\" onfocus=\"setupOptionAutocomplete(this,'" +this.optionNames +"','"
                    + this.optionValues + "')\" placeholder=\""
			        + Context.getMessageSourceService().getMessage("htmlformentry.form.value.placeholder") + "\" />");

			sb.append("\n<input type=\"hidden\" id=\"" + id + "_hid" + "\" name=\""
			        + id + "\" value=\"" + ((initialOption != null) ? initialOption.getValue() : "")
			        + "\" />");

            return sb.toString();
        }
    }
}
