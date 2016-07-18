package org.mobicents.restcomm.android.client.sdk.SignalingClient;

import android.javax.sip.Transaction;
import android.javax.sip.header.CallIdHeader;

import java.util.HashMap;
import java.util.Map;

// Handles live JAIN SIP transactions. Each transaction is stored in 'transactions' map identified by an 'id' provided by the caller (in our RCDevice or RCConnection)
// and keeps information such as the JAIN SIP Transaction (Client or Server) amongst other things
class JainSipJobManager {
   // TODO: consider using interface instead
   JainSipClient jainSipClient;
   HashMap<String, JainSipJob> jobs;


   JainSipJobManager(JainSipClient jainSipClient)
   {
      this.jainSipClient = jainSipClient;
      jobs = new HashMap<>();
   }

   JainSipJob add(String id, JainSipJob.Type type, Transaction transaction, HashMap<String, Object> parameters, JainSipCall jainSipCall)
   {
      //JainSipJob jainSipJob = new JainSipJob(this, jainSipClient, id, type, registrationType, transaction, parameters);
      JainSipJob jainSipJob = new JainSipJob(this, jainSipClient, id, type, transaction, parameters, jainSipCall);
      jobs.put(id, jainSipJob);
      jainSipJob.processFsm(id, "", null, null, null);
      return jainSipJob;
   }

   JainSipJob add(String id, JainSipJob.Type type, HashMap<String, Object> parameters)
   {
      return add(id, type, null, parameters, null);
   }

   JainSipJob add(String id, JainSipJob.Type type, HashMap<String, Object> parameters, JainSipCall jainSipCall)
   {
      return add(id, type, null, parameters, jainSipCall);
   }

   JainSipJob get(String id)
   {
      if (jobs.containsKey(id)) {
         return jobs.get(id);
      }
      else {
         return null;
      }
   }

   JainSipJob getByBranchId(String branchId)
   {
      for (Map.Entry<String, JainSipJob> entry : jobs.entrySet()) {
         JainSipJob job = entry.getValue();
         if (job.transaction.getBranchId().equals(branchId)) {
            return job;
         }
      }
      return null;
   }

   JainSipJob getByCallId(String callId)
   {
      for (Map.Entry<String, JainSipJob> entry : jobs.entrySet()) {
         JainSipJob job = entry.getValue();

         if (((CallIdHeader)job.transaction.getRequest().getHeader("Call-ID")).getCallId().equals(callId)) {
            return job;
         }
      }
      return null;
   }

   void remove(String id)
   {
      if (jobs.containsKey(id)) {
         jobs.remove(id);
      }
   }

   void removeAll()
   {
      jobs.clear();
   }

}
