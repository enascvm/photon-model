
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SessionEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SessionEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}Event"&gt;
 *       &lt;sequence&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SessionEvent")
@XmlSeeAlso({
    ServerStartedSessionEvent.class,
    UserLoginSessionEvent.class,
    UserLogoutSessionEvent.class,
    BadUsernameSessionEvent.class,
    AlreadyAuthenticatedSessionEvent.class,
    NoAccessUserEvent.class,
    SessionTerminatedEvent.class,
    GlobalMessageChangedEvent.class
})
public class SessionEvent
    extends Event
{


}
