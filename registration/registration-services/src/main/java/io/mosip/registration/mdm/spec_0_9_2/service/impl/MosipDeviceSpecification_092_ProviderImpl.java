package io.mosip.registration.mdm.spec_0_9_2.service.impl;

import static io.mosip.registration.constants.LoggerConstants.MOSIP_BIO_DEVICE_MANAGER;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.Consts;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.packetmanager.dto.BiometricsDto;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.LoggerConstants;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.mdm.MdmDeviceInfo;
import io.mosip.registration.mdm.constants.MosipBioDeviceConstants;
import io.mosip.registration.mdm.dto.CaptureRequestDeviceDetailDto;
import io.mosip.registration.mdm.dto.MDMRequestDto;
import io.mosip.registration.mdm.dto.MdmBioDevice;
import io.mosip.registration.mdm.integrator.MosipDeviceSpecificationProvider;
import io.mosip.registration.mdm.service.impl.MosipDeviceSpecificationFactory;
import io.mosip.registration.mdm.spec_0_9_2.dto.request.RCaptureRequestBioDTO;
import io.mosip.registration.mdm.spec_0_9_2.dto.request.RCaptureRequestDTO;
import io.mosip.registration.mdm.spec_0_9_2.dto.request.StreamBioRequestDTO;
import io.mosip.registration.mdm.spec_0_9_2.dto.request.StreamRequestDTO;
import io.mosip.registration.mdm.spec_0_9_2.dto.response.DigitalId;
import io.mosip.registration.mdm.spec_0_9_2.dto.response.MdmDeviceInfoResponse;
import io.mosip.registration.mdm.spec_0_9_2.dto.response.RCaptureResponseBiometricsDTO;
import io.mosip.registration.mdm.spec_0_9_2.dto.response.RCaptureResponseDTO;
import io.mosip.registration.mdm.spec_0_9_2.dto.response.RCaptureResponseDataDTO;

@Service
public class MosipDeviceSpecification_092_ProviderImpl implements MosipDeviceSpecificationProvider {

	private static final Logger LOGGER = AppConfig.getLogger(MosipDeviceSpecification_092_ProviderImpl.class);

	private static final String SPEC_VERSION = "0.9.2";

	@Autowired
	private MosipDeviceSpecificationFactory deviceSpecificationFactory;

	@Override
	public String getSpecVersion() {
		return SPEC_VERSION;
	}

	@Override
	public List<MdmBioDevice> getMdmDevices(String deviceInfoResponse) {

		List<MdmBioDevice> mdmBioDevices = new LinkedList<>();

		List<MdmDeviceInfo> mdmDeviceInfos = new LinkedList<>();

		List<MdmDeviceInfoResponse> deviceInfoResponses;
		try {
			deviceInfoResponses = (deviceSpecificationFactory.getMapper().readValue(deviceInfoResponse,
					new TypeReference<List<MdmDeviceInfoResponse>>() {
					}));

			for (MdmDeviceInfoResponse mdmDeviceInfoResponse : deviceInfoResponses) {

				if (mdmDeviceInfoResponse.getDeviceInfo() != null && !mdmDeviceInfoResponse.getDeviceInfo().isEmpty()) {
					mdmDeviceInfos.add(getDeviceInfoDecoded(mdmDeviceInfoResponse.getDeviceInfo()));
				}
			}

			for (MdmDeviceInfo deviceInfo : mdmDeviceInfos) {

				MdmBioDevice bioDevice = getBioDevice(deviceInfo);

				if (bioDevice != null) {
					mdmBioDevices.add(bioDevice);

				}

			}
		} catch (Exception exception) {
			LOGGER.error(LoggerConstants.LOG_SERVICE_DELEGATE_UTIL_GET, APPLICATION_NAME, APPLICATION_ID,
					String.format(" Exception while mapping the response ",
							exception.getMessage() + ExceptionUtils.getStackTrace(exception)));
		}

		return mdmBioDevices;
	}

	@Override
	public InputStream stream(MdmBioDevice bioDevice, String modality) throws MalformedURLException, IOException {

		String url = bioDevice.getCallbackId() +"/"+ MosipBioDeviceConstants.STREAM_ENDPOINT;

		StreamRequestDTO streamRequestDTO = getStreamRequestDTO(bioDevice, modality);

		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
		con.setRequestMethod("POST");
		String request = new ObjectMapper().writeValueAsString(streamRequestDTO);

		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());

		LOGGER.info("BioDevice", APPLICATION_NAME, APPLICATION_ID,
				"Stream Request Started : " + System.currentTimeMillis());
		LOGGER.info("BioDevice", APPLICATION_NAME, APPLICATION_ID, "Stream Request :" + streamRequestDTO.toString());

		wr.writeBytes(request);
		wr.flush();
		wr.close();
		con.setReadTimeout(5000);
		con.connect();
		InputStream urlStream = con.getInputStream();
		LOGGER.info("BioDevice", APPLICATION_NAME, APPLICATION_ID,
				"Leaving into Stream Method.... " + System.currentTimeMillis());
		return urlStream;

	}

	private StreamRequestDTO getStreamRequestDTO(MdmBioDevice bioDevice, String modality) {

		StreamRequestDTO bioCaptureRequestDto = new StreamRequestDTO();

		bioCaptureRequestDto.setEnv("Staging");
		bioCaptureRequestDto.setPurpose(bioDevice.getPurpose());
		bioCaptureRequestDto.setSpecVersion(bioDevice.getSpecVersion());
		bioCaptureRequestDto.setTimeout(1000000);
		bioCaptureRequestDto.setRegistrationID(String.valueOf(deviceSpecificationFactory.generateID()));

		StreamBioRequestDTO mosipBioRequest = new StreamBioRequestDTO();
		mosipBioRequest.setType(getDevicCode(bioDevice.getDeviceType()));
		mosipBioRequest.setCount(1);
		mosipBioRequest.setRequestedScore(40);
		String exception[] = new String[0];

		mosipBioRequest.setException(exception);
		mosipBioRequest.setDeviceId(bioDevice.getDeviceId());
		mosipBioRequest.setDeviceSubId(getDeviceSubId(modality));
		mosipBioRequest.setPreviousHash("");
		bioCaptureRequestDto
				.setCaptureTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).toString());

		List<StreamBioRequestDTO> bioRequests = new ArrayList<>();
		bioRequests.add(mosipBioRequest);

		bioCaptureRequestDto.setMosipBioRequest(bioRequests);

		Map<String, String> customOpts = new HashMap<String, String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			{
				put("Name", "name1");
				put("Value", "value1");
			}
		};

		bioCaptureRequestDto.setCustomOpts(Arrays.asList(customOpts));

		return bioCaptureRequestDto;

	}

	@Override
	public List<BiometricsDto> rCapture(MdmBioDevice bioDevice, MDMRequestDto mdmRequestDto)
			throws JsonParseException, JsonMappingException, ParseException, IOException {

		if (mdmRequestDto.getExceptions() != null) {
			mdmRequestDto.setExceptions(getExceptions(mdmRequestDto.getExceptions()));
		}

		RCaptureRequestDTO rCaptureRequestDTO = getRCaptureRequest(bioDevice, mdmRequestDto);

		LOGGER.info("BioDevice", APPLICATION_NAME, APPLICATION_ID,
				"Entering into Capture method....." + System.currentTimeMillis());

		String requestBody = null;
		ObjectMapper mapper = new ObjectMapper();
		requestBody = mapper.writeValueAsString(rCaptureRequestDTO);

		LOGGER.info("BioDevice", APPLICATION_NAME, APPLICATION_ID, "Request for RCapture...." + requestBody);

		CloseableHttpClient client = HttpClients.createDefault();
		StringEntity requestEntity = new StringEntity(requestBody, ContentType.create("Content-Type", Consts.UTF_8));
		LOGGER.info("BioDevice", APPLICATION_NAME, APPLICATION_ID,
				"Bulding capture url...." + System.currentTimeMillis());
		HttpUriRequest request = RequestBuilder.create("RCAPTURE")
				.setUri(bioDevice.getCallbackId() + MosipBioDeviceConstants.CAPTURE_ENDPOINT).setEntity(requestEntity)
				.build();
		LOGGER.info("BioDevice", APPLICATION_NAME, APPLICATION_ID,
				"Requesting capture url...." + System.currentTimeMillis());
		CloseableHttpResponse response = client.execute(request);
		LOGGER.info("BioDevice", APPLICATION_NAME, APPLICATION_ID,
				"Request completed.... " + System.currentTimeMillis());

		String val = EntityUtils.toString(response.getEntity());

		RCaptureResponseDTO captureResponse = mapper.readValue(val.getBytes(StandardCharsets.UTF_8),
				RCaptureResponseDTO.class);
		LOGGER.info("BioDevice", APPLICATION_NAME, APPLICATION_ID,
				"Response Recived.... " + System.currentTimeMillis());

		LOGGER.info("BioDevice", APPLICATION_NAME, APPLICATION_ID,
				"Response Decode and leaving the method.... " + System.currentTimeMillis());

		List<RCaptureResponseBiometricsDTO> captureResponseBiometricsDTOs = captureResponse.getBiometrics();

		List<BiometricsDto> biometricDTOs = new LinkedList<>();

		for (RCaptureResponseBiometricsDTO rCaptureResponseBiometricsDTO : captureResponseBiometricsDTOs) {

			String payLoad = deviceSpecificationFactory.getPayLoad(rCaptureResponseBiometricsDTO.getData());

			RCaptureResponseDataDTO dataDTO = (RCaptureResponseDataDTO) (mapper
					.readValue(new String(Base64.getUrlDecoder().decode(payLoad)), RCaptureResponseDataDTO.class));

			BiometricsDto biometricDTO = new BiometricsDto(dataDTO.getBioSubType(), dataDTO.getDecodedBioValue(),
					Double.parseDouble(dataDTO.getQualityScore()));
			biometricDTO.setCaptured(true);
			biometricDTO.setModalityName(mdmRequestDto.getModality());
			biometricDTOs.add(biometricDTO);
		}
		return null;
	}

	private String[] getExceptions(String[] exceptions) {

		if (exceptions != null) {
			for (int index = 0; index < exceptions.length; index++) {
				exceptions[index] = io.mosip.registration.mdm.dto.Biometric
						.getmdmRequestAttributeName(exceptions[index], SPEC_VERSION);
			}

		}

		return exceptions;

	}

	private RCaptureRequestDTO getRCaptureRequest(MdmBioDevice bioDevice, MDMRequestDto mdmRequestDto)
			throws JsonParseException, JsonMappingException, IOException {

		RCaptureRequestDTO rCaptureRequestDTO = null;

		if (bioDevice != null) {
			List<RCaptureRequestBioDTO> captureRequestBioDTOs = new LinkedList<>();
			captureRequestBioDTOs.add(new RCaptureRequestBioDTO(bioDevice.getDeviceType(), "1", null,
					mdmRequestDto.getExceptions(), String.valueOf(mdmRequestDto.getRequestedScore()),
					bioDevice.getDeviceId(), getDeviceSubId(mdmRequestDto.getModality()), null));

			rCaptureRequestDTO = new RCaptureRequestDTO(mdmRequestDto.getEnvironment(), "Registration", "0.9.5",
					String.valueOf(mdmRequestDto.getTimeout()),
					LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).toString(),
					String.valueOf(deviceSpecificationFactory.generateID()), captureRequestBioDTOs, null);
		}

		return rCaptureRequestDTO;
	}

	private String getDeviceSubId(String modality) {
		modality = modality.toLowerCase();

		return modality.contains("left") ? "1"
				: modality.contains("right") ? "2"
						: (modality.contains("double") || modality.contains("thumbs") || modality.contains("two")) ? "3"
								: modality.contains("face") ? "0" : "0";
	}

	private MdmDeviceInfo getDeviceInfoDecoded(String deviceInfo) {
		try {
			String result = new String(
					Base64.getUrlDecoder().decode(deviceSpecificationFactory.getPayLoad(deviceInfo)));
			return (MdmDeviceInfo) (deviceSpecificationFactory.getMapper().readValue(result, MdmDeviceInfo.class));
		} catch (Exception exception) {
			LOGGER.error(LoggerConstants.LOG_SERVICE_DELEGATE_UTIL_GET, APPLICATION_NAME, APPLICATION_ID,
					String.format("%s -> Exception while trying to extract the response through regex  %s",
							exception.getMessage() + ExceptionUtils.getStackTrace(exception)));

		}
		return null;

	}

	private MdmBioDevice getBioDevice(MdmDeviceInfo deviceInfo)
			throws JsonParseException, JsonMappingException, IOException {

		MdmBioDevice bioDevice = null;

		if (deviceInfo != null) {

			DigitalId digitalId = getDigitalId(deviceInfo.getDigitalId());

			bioDevice = new MdmBioDevice();
			bioDevice.setDeviceId(deviceInfo.getDeviceId());
			bioDevice.setFirmWare(deviceInfo.getFirmware());
			bioDevice.setCertification(deviceInfo.getCertification());
			bioDevice.setSerialVersion(deviceInfo.getServiceVersion());
			bioDevice.setSpecVersion(deviceSpecificationFactory.getLatestSpecVersion(deviceInfo.getSpecVersion()));
			bioDevice.setPurpose(deviceInfo.getPurpose());
			bioDevice.setDeviceCode(deviceInfo.getDeviceCode());

			bioDevice.setDeviceSubType(digitalId.getSubType());
			bioDevice.setDeviceType(digitalId.getType());
			bioDevice.setTimestamp(digitalId.getDateTime());
			bioDevice.setDeviceProviderName(digitalId.getDeviceProvider());
			bioDevice.setDeviceProviderId(digitalId.getDeviceProviderId());
			bioDevice.setDeviceModel(digitalId.getModel());
			bioDevice.setDeviceMake(digitalId.getMake());

			bioDevice.setCallbackId(deviceInfo.getCallbackId());
		}

		LOGGER.info(MOSIP_BIO_DEVICE_MANAGER, APPLICATION_NAME, APPLICATION_ID, "Adding Device to Registry : ");
		return bioDevice;
	}

	private DigitalId getDigitalId(String digitalId) throws JsonParseException, JsonMappingException, IOException {
		return (DigitalId) (deviceSpecificationFactory.getMapper().readValue(
				new String(Base64.getUrlDecoder().decode(deviceSpecificationFactory.getPayLoad(digitalId))),
				DigitalId.class));

	}

	private static String getDevicCode(String deviceType) {
		switch (deviceType.toUpperCase()) {
		case RegistrationConstants.FINGERPRINT_UPPERCASE:
			deviceType = "FIR";
			break;

		case RegistrationConstants.IRIS:
			deviceType = "IIR";
			break;
		}

		return deviceType;

	}
}