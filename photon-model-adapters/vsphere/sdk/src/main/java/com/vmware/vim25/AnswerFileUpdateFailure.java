
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AnswerFileUpdateFailure complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AnswerFileUpdateFailure"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="userInputPath" type="{urn:vim25}ProfilePropertyPath"/&gt;
 *         &lt;element name="errMsg" type="{urn:vim25}LocalizableMessage"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AnswerFileUpdateFailure", propOrder = {
    "userInputPath",
    "errMsg"
})
public class AnswerFileUpdateFailure
    extends DynamicData
{

    @XmlElement(required = true)
    protected ProfilePropertyPath userInputPath;
    @XmlElement(required = true)
    protected LocalizableMessage errMsg;

    /**
     * Gets the value of the userInputPath property.
     * 
     * @return
     *     possible object is
     *     {@link ProfilePropertyPath }
     *     
     */
    public ProfilePropertyPath getUserInputPath() {
        return userInputPath;
    }

    /**
     * Sets the value of the userInputPath property.
     * 
     * @param value
     *     allowed object is
     *     {@link ProfilePropertyPath }
     *     
     */
    public void setUserInputPath(ProfilePropertyPath value) {
        this.userInputPath = value;
    }

    /**
     * Gets the value of the errMsg property.
     * 
     * @return
     *     possible object is
     *     {@link LocalizableMessage }
     *     
     */
    public LocalizableMessage getErrMsg() {
        return errMsg;
    }

    /**
     * Sets the value of the errMsg property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocalizableMessage }
     *     
     */
    public void setErrMsg(LocalizableMessage value) {
        this.errMsg = value;
    }

}
