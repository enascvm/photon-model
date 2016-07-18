
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AnswerFileOptionsCreateSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AnswerFileOptionsCreateSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}AnswerFileCreateSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="userInput" type="{urn:vim25}ProfileDeferredPolicyOptionParameter" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AnswerFileOptionsCreateSpec", propOrder = {
    "userInput"
})
public class AnswerFileOptionsCreateSpec
    extends AnswerFileCreateSpec
{

    protected List<ProfileDeferredPolicyOptionParameter> userInput;

    /**
     * Gets the value of the userInput property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the userInput property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getUserInput().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ProfileDeferredPolicyOptionParameter }
     * 
     * 
     */
    public List<ProfileDeferredPolicyOptionParameter> getUserInput() {
        if (userInput == null) {
            userInput = new ArrayList<ProfileDeferredPolicyOptionParameter>();
        }
        return this.userInput;
    }

}
