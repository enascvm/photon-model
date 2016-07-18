
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ProfileUpdateFailedUpdateFailure complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ProfileUpdateFailedUpdateFailure"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="profilePath" type="{urn:vim25}ProfilePropertyPath"/&gt;
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
@XmlType(name = "ProfileUpdateFailedUpdateFailure", propOrder = {
    "profilePath",
    "errMsg"
})
public class ProfileUpdateFailedUpdateFailure
    extends DynamicData
{

    @XmlElement(required = true)
    protected ProfilePropertyPath profilePath;
    @XmlElement(required = true)
    protected LocalizableMessage errMsg;

    /**
     * Gets the value of the profilePath property.
     * 
     * @return
     *     possible object is
     *     {@link ProfilePropertyPath }
     *     
     */
    public ProfilePropertyPath getProfilePath() {
        return profilePath;
    }

    /**
     * Sets the value of the profilePath property.
     * 
     * @param value
     *     allowed object is
     *     {@link ProfilePropertyPath }
     *     
     */
    public void setProfilePath(ProfilePropertyPath value) {
        this.profilePath = value;
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
