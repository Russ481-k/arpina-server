package egov.com.cmm.web;

/*
 * Copyright 2001-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the ";License&quot;);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS"; BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.commons.fileupload.FileItem;
import org.springframework.lang.NonNull;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import egov.com.cmm.util.EgovFileUploadUtil;
import egov.com.cmm.service.EgovProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 실행환경의 파일업로드 처리를 위한 기능 클래스
 *
 * @author 공통서비스개발팀 이삼섭
 * @since 2009.06.01
 * @version 1.0
 * @see
 *
 *      <pre>
 * << 개정이력(Modification Information) >>
 *
 *  수정일                수정자             수정내용
 *  ----------   --------    ---------------------------
 *  2009.03.25   이삼섭              최초 생성
 *  2011.06.11   서준식              스프링 3.0 업그레이드 API변경으로인한 수정
 *  2020.10.27   신용호              예외처리 수정
 *  2020.10.29   신용호              허용되지 않는 확장자 업로드 제한 (globals.properties > Globals.fileUpload.Extensions)
 *
 *      </pre>
 */
@Slf4j
public class EgovMultipartResolver extends CommonsMultipartResolver {

	public EgovMultipartResolver() {
	}

	/**
	 * 첨부파일 처리를 위한 multipart resolver를 생성한다.
	 *
	 * @param servletContext
	 */
	public EgovMultipartResolver(ServletContext servletContext) {
		super(servletContext);
	}

	/**
	 * multipart에 대한 parsing을 처리한다.
	 */
	@Override
	@NonNull
	protected MultipartParsingResult parseFileItems(@NonNull List<FileItem> fileItems, @NonNull String encoding) {
		log.debug("--- EgovMultipartResolver.parseFileItems START ---");
		log.debug("Received {} file items for parsing. Encoding: {}", fileItems.size(), encoding);

		// Log details of each received FileItem *before* filtering
		for (FileItem item : fileItems) {
			log.debug("  Item: Name={}, FieldName={}, IsFormField={}, Size={}, ContentType={}", 
					  item.getName(), item.getFieldName(), item.isFormField(), item.getSize(), item.getContentType());
		}

		// 스프링 3.0변경으로 수정한 부분
		MultiValueMap<String, MultipartFile> multipartFiles = new LinkedMultiValueMap<String, MultipartFile>();
		Map<String, String[]> multipartParameters = new HashMap<String, String[]>();
		String whiteListFileUploadExtensions = EgovProperties.getProperty("Globals.fileUpload.Extensions");
		Map<String, String> mpParamContentTypes = new HashMap<String, String>();

		// Extract multipart files and multipart parameters.
		for (final FileItem fileItem : fileItems) {
			if (fileItem.isFormField()) {
				String value = null;
				if (encoding != null) {
					try {
						value = fileItem.getString(encoding);
					} catch (UnsupportedEncodingException ex) {
						log.warn("Could not decode multipart item '{}' with encoding '{}': using platform default",
								fileItem.getFieldName(), encoding);
						value = fileItem.getString();
					}
				} else {
					value = fileItem.getString();
				}
				String[] curParam = multipartParameters.get(fileItem.getFieldName());
				if (curParam == null) {
					// simple form field
					multipartParameters.put(fileItem.getFieldName(), new String[] { value });
				} else {
					// array of simple form fields
					String[] newParam = StringUtils.addStringToArray(curParam, value);
					multipartParameters.put(fileItem.getFieldName(), newParam);
				}

				//contentType 입력
				mpParamContentTypes.put(fileItem.getFieldName(), fileItem.getContentType());
				log.debug("  Processing form field: Name={}", fileItem.getFieldName());
			} else {
				// Process multipart file.
				final String fileName = fileItem.getName();
				if (StringUtils.hasText(fileName)) {
					// Restore original extension check logic
				String fileExtension = EgovFileUploadUtil.getFileExtension(fileName);
					boolean check = false;
				if (whiteListFileUploadExtensions == null || "".equals(whiteListFileUploadExtensions)) {
						log.debug("  File extension whitelist is not set. Allowing file: {}", fileName);
						check = true; // 허용 목록 없으면 통과
					} else {
						 if (StringUtils.hasText(fileExtension)) { // 확장자가 있는 경우
							if ((whiteListFileUploadExtensions+".").toLowerCase().contains("."+fileExtension.toLowerCase()+".")) {
								check = true;
								log.debug("  File extension check for '{}': '{}' is allowed.", fileName, fileExtension);
						} else {
								log.warn("  Rejected file due to invalid extension: File='{}', Extension='{}'. Allowed: '{}'", fileName, fileExtension, whiteListFileUploadExtensions);
								check = false;
							}
						} else { // 확장자가 없는 경우
							 log.warn("  Rejected file due to missing extension: File='{}'", fileName);
							 check = false;
						}
					}
					
					if (check) {
						CommonsMultipartFile file = new CommonsMultipartFile(fileItem);
						multipartFiles.add(fileItem.getFieldName(), file); // Use field name directly
						log.debug("  Added valid file to map: Key='{}', File='{}'", fileItem.getFieldName(), file.getOriginalFilename());
						}
				} else {
					 log.debug("  Skipping file item with empty name: FieldName={}", fileItem.getFieldName());
				}
			}
		}

		log.debug("--- EgovMultipartResolver.parseFileItems END ---: Parsed {} files, {} parameters.", multipartFiles.size(), multipartParameters.size());
		return new MultipartParsingResult(multipartFiles, multipartParameters, mpParamContentTypes);
	}
}
