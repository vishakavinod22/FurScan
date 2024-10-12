package furscan.furscan.Services;

import java.util.List;
import java.util.Optional;
import furscan.furscan.Utils.EmailSenderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import furscan.furscan.Entity.MstUsers;
import furscan.furscan.Entity.PetReport;
import furscan.furscan.Repository.UserRepository;
import furscan.furscan.Repository.VetRepository;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class VetService{

    @Autowired
    private VetRepository vetRepository;

    @Autowired
    private EmailSenderUtil senderUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    /**
     * List of the all the Pet report request
     * @return
     */
    public List<PetReport> index() {
        List<PetReport> petData = vetRepository.is_SendReport();
        if(petData.isEmpty()) {
            return null;
        }
        for(PetReport petReport : petData) {
            String key = petReport.getImage_text();
            
            System.out.println("key " + key);
            String reportKey = petReport.getReport_text();
            String reportUrl = "https://furscan.s3.amazonaws.com/" + reportKey;
            System.out.println("Report text " + reportKey);
            petReport.setReport_text(reportUrl);
        }
        return petData;
    }

    /**
     * Details by ID.
     * @param id
     * @return
     */
    public Optional<PetReport> details(Integer id) {
        Optional<PetReport> petReport = vetRepository.findById(id);
        if(petReport.isEmpty()) {
            return null;
        }
        return petReport;
    }

    /**
     * Doctor giving the remarks on the Pet's condition generated by the ML model
     * @param id
     * @param remark
     * @return
     */
    public PetReport updateReport(Integer id, String remark){
        try {
            PetReport petReport = vetRepository.findByPet_report_id(id);
            petReport.setRemarks(remark);
            petReport = vetRepository.save(petReport);
            String response = updatedPdfRequest(id);
            System.out.println(response);
            Integer user_id = petReport.getUser_id();
            MstUsers mstUsers = userRepository.findById(user_id).orElse(null);
            String subject = "Doctor commented on your pet";
            String body= "Hello, " + mstUsers.getFirst_name() +"!\n\n"
            + "Your pet report is ready please find the attachment below. "
            + "We hope you're having a great day!\n\n"
            + "Best regards,\n"
            + "FurScan Team";
            String to = mstUsers.getEmail();

            JsonNode root = objectMapper.readTree(response);
            String attachment = "https://furscan.s3.amazonaws.com/" + root.get("report_object_key").asText();
            System.out.println(attachment);
            triggerEmail(to, body, subject, attachment);
            if(response == null) {
                return null;
            }
            return petReport;
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Request to get the updated report generated from the ML model.
     * @param pet_report_id
     * @return
     */
    private String updatedPdfRequest(Integer pet_report_id) {
        String Url = "http://52.23.167.187/add_doctor_remarks/"+pet_report_id;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.postForObject(Url, request,String.class);

        return response;
    }

    /**
     * Helper function to trigger email.
     * @param to
     * @param body
     * @param subject
     * @param attachment
     */
    public void triggerEmail(String to, String body, String subject, String attachment) {
        senderUtil.sendEmailWithAttachment(to, body, subject, attachment);
    }

}